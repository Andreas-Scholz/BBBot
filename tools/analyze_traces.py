#!/usr/bin/env python3
"""Works out from the bot's traces what the game pays for and which pieces it hands out.

    adb pull /sdcard/Android/data/io.github.andreasscholz.bbbot/files/captures captures
    python tools/analyze_traces.py captures/trace-*

A trace is a folder with a `moves.csv`, one line per piece placed (see the README). The game
counts the score up with a delay: the points for placing blocks arrive a second or two late, the
points for a clear over several seconds. With "measure exactly" switched on in the app, the bot
waits after every clear until the score stands still, so the score after a clear is settled. What
a clear earned is therefore worked out from one settled score to the next: the difference, less a
point for every block placed in between.

Needs nothing but Python 3.
"""
import csv
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path

SIZE = 8


def shape(mask):
    """A piece as rows of text, from its number with one bit per cell."""
    rows = []
    for r in range(SIZE):
        bits = (mask >> (r * SIZE)) & 0xFF
        if mask >> (r * SIZE) == 0:
            break
        rows.append(bits)
    width = max(b.bit_length() for b in rows)
    return '/'.join(''.join('X' if b >> c & 1 else '.' for c in range(width)) for b in rows)


def read(folder):
    path = Path(folder) / 'moves.csv'
    if not path.exists():
        return []
    with open(path, newline='') as f:
        rows = list(csv.DictReader(f))
    if rows and 'board' not in rows[0]:
        print(f'{folder}: an older trace without boards and scores, skipped')
        return []
    for row in rows:
        row['trace'] = Path(folder).name
        row['score'] = int(row['score']) if row['score'] else None
        row['stable'] = row['score_stable'] == 'true'
        row['lines'] = int(row['lines']) if row['lines'] != '' else None
        row['cells'] = int(row['cells']) if row['cells'] != '' else None
        row['board'] = int(row['board'], 16)
        row['offered_slots'] = [(int(p.split(':')[0]), int(p.split(':')[1], 16)) for p in row['offered'].split(';') if p]
        row['offered'] = [mask for _, mask in row['offered_slots']]
        row['all_clear'] = row['all_clear'] == 'true'
        row['clear_possible'] = row['clear_possible'] == 'true'
        row['combo_length'] = int(row['combo_length'])
        row['round'] = int(row['round'])
    return rows


def earned(rows):
    """Adds to every clearing piece what its clear earned, where that is known for sure.

    Settled scores are the ones read right after a clear, when the bot has waited. Between two of
    them lie some pieces that cleared nothing and, last, one that cleared something.
    """
    for row in rows:
        row['earned'] = None
    settled = [i for i in range(1, len(rows))
               if rows[i - 1]['lines'] and rows[i]['stable'] and rows[i]['score'] is not None
               and rows[i]['previous_as_expected'] == 'true']
    for start, end in zip(settled, settled[1:]):
        between = rows[start:end]
        if any(r['lines'] is None for r in between) or sum(1 for r in between if r['lines']) != 1:
            continue
        if any(r['previous_as_expected'] != 'true' for r in between[1:]):
            continue
        blocks = sum(r['cells'] for r in between)
        rows[end - 1]['earned'] = rows[end]['score'] - rows[start]['score'] - blocks
        rows[end - 1]['blocks_before'] = blocks - rows[end - 1]['cells']


def stretches(rows):
    """Cuts a trace where the combo ended: after every round of three pieces that cleared nothing."""
    by_round = defaultdict(list)
    for row in rows:
        if row['lines'] is not None:
            by_round[row['round']].append(row)
    lost = {number for number, pieces in by_round.items() if len(pieces) >= 3 and not any(p['lines'] for p in pieces)}
    current = []
    for row in rows:
        if row['lines'] is None:
            continue
        if row['round'] in lost:
            if current:
                yield current
            current = []
        else:
            current.append(row)
    if current:
        yield current


def section(title):
    print()
    print(title)
    print('-' * len(title))


def main(folders):
    traces = [rows for rows in (read(f) for f in folders) if rows]
    if not traces:
        print('No traces with scores found.')
        return
    for rows in traces:
        earned(rows)
    everything = [row for rows in traces for row in rows]
    known = [row for row in everything if row.get('earned') is not None]
    print(f'{len(traces)} traces, {sum(r["lines"] is not None for r in everything)} pieces placed, '
          f'{sum(1 for r in everything if r["lines"])} of them cleared something, '
          f'{len(known)} clears with a known gain')
    if not known:
        print('No clear has a known gain. Was "measure exactly" switched on?')

    section('What a clear is worth, by the number of lines')
    # The worth of a line grows with the combo. To compare clears made at different times, each
    # is divided by what a single line was worth at that moment: the straight line through the
    # gains of the single-line clears around it, over the count of clearing pieces. A round
    # without a clear ends the combo and starts the count anew, so a trace is cut there.
    ratios = defaultdict(list)
    steps = []
    for rows in traces:
        for stretch in stretches(rows):
            clears = 0
            points = []
            for row in stretch:
                if row['lines']:
                    row['clear_index'] = clears
                    clears += 1
                    if row.get('earned') is not None and row['lines'] == 1 and not row['all_clear']:
                        points.append((row['clear_index'], row['earned']))
            if len(points) < 4:
                continue
            # A score taken for settled too early puts the gain of a clear into the next one.
            # Such pairs show as a clear worth nothing next to one worth double; a first straight
            # line through everything shows which clears those are, and they are left out.
            slope, intercept = statistics.linear_regression([p[0] for p in points], [p[1] for p in points])
            sound = [p for p in points if abs(p[1] - (intercept + slope * p[0])) <= 0.2 * max(intercept + slope * p[0], 50)]
            if len(sound) < 4:
                continue
            slope, intercept = statistics.linear_regression([p[0] for p in sound], [p[1] for p in sound])
            off = max(abs(y - (intercept + slope * x)) for x, y in sound)
            if off > 5:
                continue
            steps.append(slope)
            print(f'{stretch[0]["trace"]} from piece {stretch[0]["move"]}: a single line earned {intercept:,.1f} points '
                  f'and {slope:+.2f} more with every clearing piece ({len(sound)} single clears within {off:.1f} points'
                  f'{", " + str(len(points) - len(sound)) + " left out" if len(sound) < len(points) else ""}); '
                  f'combo {intercept / slope:.1f} long at its start')
            for row in stretch:
                if row.get('earned') is None or not row['lines']:
                    continue
                single = intercept + slope * row['clear_index']
                ratio = row['earned'] / single
                # Left out as above: gains that are half or one and a half of what they should be.
                if abs(ratio - round(ratio)) < 0.1 and ratio > 0.5:
                    ratios[(row['lines'], row['all_clear'])].append(ratio)
    if steps:
        print(f'The step per clearing piece: {statistics.median(steps):.2f} points (median of {len(steps)} stretches)')
    for (lines, all_clear), values in sorted(ratios.items()):
        what = f'{lines} line{"s" if lines > 1 else " "}' + (' emptying the board' if all_clear else '')
        common = Counter(round(v) for v in values).most_common(3)
        print(f'{what:32} {len(values):4} clears   worth ' + ', '.join(f'{k} single lines ({v}x)' for k, v in common))

    section('Rounds without a clear, and what the next clear earned')
    for rows in traces:
        by_round = defaultdict(list)
        for row in rows:
            if row['lines'] is not None:
                by_round[row['round']].append(row)
        for number in sorted(by_round):
            pieces = by_round[number]
            if len(pieces) < 3 or any(p['lines'] for p in pieces):
                continue
            before = [r for r in rows if r.get('earned') is not None and r['lines'] and r['round'] < number]
            after = [r for r in rows if r.get('earned') is not None and r['lines'] and r['round'] > number]
            if before and after:
                b, a = before[-1], after[0]
                print(f'{rows[0]["trace"]} round {number} (clear possible: {pieces[0]["clear_possible"]}): '
                      f'before it {b["lines"]} line(s) earned {b["earned"]:,}, after it {a["lines"]} line(s) earned {a["earned"]:,}')

    section('The pieces the game hands out')
    # A piece is read anew before every piece of its round. The first look of a round may catch
    # the pieces while they are still growing into place, so the last reading of each counts.
    rounds = []
    for rows in traces:
        current = None
        for row in rows:
            if row['lines'] is None:
                continue
            if current is None or current['round'] != row['round']:
                if len(row['offered_slots']) != 3:
                    current = None
                    continue
                current = {'round': row['round'], 'board': row['board'], 'clear_possible': row['clear_possible'],
                           'slots': dict(row['offered_slots']), 'first': dict(row['offered_slots'])}
                rounds.append(current)
            else:
                current['slots'].update(row['offered_slots'])
    for r in rounds:
        r['offered'] = list(r['slots'].values())
    misread = sum(1 for r in rounds if r['slots'] != r['first'])
    counts = Counter(mask for row in rounds for mask in row['offered'])
    total = sum(counts.values())
    print(f'{len(rounds)} rounds, {total} pieces, {len(counts)} different shapes; '
          f'in {misread} rounds a piece was read differently at first than later')
    for mask, count in counts.most_common():
        print(f'{100 * count / total:5.1f} %  {count:5}  {mask:>8x}  {shape(mask)}')
    if rounds:
        possible = sum(row['clear_possible'] for row in rounds)
        print(f'In {100 * possible / len(rounds):.0f} % of the rounds the three pieces could clear a line.')
        fill = [bin(row['board']).count('1') for row in rounds]
        cells = [sum(bin(m).count('1') for m in row['offered']) for row in rounds]
        if len(rounds) > 10:
            print(f'Blocks on the board at the start of a round: {statistics.mean(fill):.1f} on average; '
                  f'blocks in the three pieces: {statistics.mean(cells):.1f}; '
                  f'correlation of the two: {statistics.correlation(fill, cells):+.2f}')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1:])
