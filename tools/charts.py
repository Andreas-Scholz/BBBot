#!/usr/bin/env python3
"""Draws the charts of the README's evaluation into docs/charts.

    python tools/charts.py --lab lab-out --traces captures --deals deals.csv

`lab-out` is what StrategyLab wrote with BBBOT_LAB_OUT set, `captures` the folder with the
phone's `trace-*` folders, and `deals.csv` what DealLab wrote with BBBOT_DEALS_OUT set (see the
README). A chart whose input is not given is left as it is.

Needs nothing but Python 3.
"""
import argparse
import csv
from collections import Counter
from pathlib import Path

W, L, R, T, B = 720, 64, 16, 44, 42
INK, MUTED, GRID, PANEL = '#1c2540', '#5d6885', '#d9deea', '#ffffff'
COLORS = {'safe': '#2f6fd6', 'balanced': '#2f9e56', 'risky': '#d8493f', 'ink': INK, 'soft': '#a9b3c9'}
FONT = "system-ui, 'Segoe UI', Helvetica, Arial, sans-serif"
STRATEGIES = [('safe', 'Safe'), ('balanced', 'Balanced'), ('risky', 'Risky')]
WORTH = {1: 1, 2: 2, 3: 6, 4: 14.4, 5: 20, 6: 30}


def millions(value):
    return f'{value / 1e6:g}'


class Chart:
    """A chart with one scale per axis; everything drawn goes through x() and y()."""

    def __init__(self, title, xmax, ymax, xticks, yticks, xlabel, ylabel, xmin=0, height=340, xfmt=str, yfmt=str):
        self.xmin, self.xmax, self.ymax, self.h = xmin, xmax, ymax, height
        self.parts = [
            f'<rect width="{W}" height="{height}" rx="8" fill="{PANEL}"/>',
            f'<text x="{L}" y="22" font-size="15" font-weight="600" fill="{INK}">{title}</text>',
        ]
        self.legend_x = L
        for y in yticks:
            py = self.y(y)
            self.parts.append(f'<line x1="{L}" x2="{W - R}" y1="{py:.1f}" y2="{py:.1f}" stroke="{GRID}"/>')
            self.parts.append(f'<text x="{L - 8}" y="{py + 4:.1f}" text-anchor="end" font-size="12" fill="{MUTED}">{yfmt(y)}</text>')
        for x in xticks:
            self.tick(x, xfmt(x))
        self.parts.append(f'<line x1="{L}" x2="{W - R}" y1="{height - B}" y2="{height - B}" stroke="{MUTED}"/>')
        self.parts.append(f'<text x="{(L + W - R) / 2}" y="{height - 6}" text-anchor="middle" font-size="12" fill="{MUTED}">{xlabel}</text>')
        self.parts.append(f'<text x="{W - R}" y="{T}" text-anchor="end" font-size="12" fill="{MUTED}">{ylabel}</text>')

    def x(self, value):
        return L + (value - self.xmin) / (self.xmax - self.xmin) * (W - L - R)

    def y(self, value):
        return self.h - B - value / self.ymax * (self.h - B - T - 26)

    def tick(self, x, text):
        self.parts.append(f'<text x="{self.x(x):.1f}" y="{self.h - B + 17}" text-anchor="middle" font-size="12" fill="{MUTED}">{text}</text>')

    def legend(self, name, color, dots=False):
        mark = (f'<circle cx="{self.legend_x + 7}" cy="{T - 4}" r="4" fill="{COLORS[color]}"/>' if dots
                else f'<rect x="{self.legend_x}" y="{T - 6}" width="16" height="4" rx="2" fill="{COLORS[color]}"/>')
        self.parts.append(mark + f'<text x="{self.legend_x + 22}" y="{T}" font-size="12" fill="{INK}">{name}</text>')
        self.legend_x += 34 + 6.6 * len(name)

    def line(self, points, color, name=None):
        d = ' '.join(f'{self.x(x):.1f},{self.y(y):.1f}' for x, y in points)
        self.parts.append(f'<polyline points="{d}" fill="none" stroke="{COLORS[color]}" stroke-width="2.2" stroke-linejoin="round"/>')
        if name:
            self.legend(name, color)

    def dots(self, points, color, name=None):
        for x, y in points:
            self.parts.append(f'<circle cx="{self.x(x):.1f}" cy="{self.y(y):.1f}" r="3.2" fill="{COLORS[color]}" fill-opacity="0.5"/>')
        if name:
            self.legend(name, color, dots=True)

    def bar(self, x, width, value, color, label=None):
        px, py = self.x(x), self.y(value)
        w = width / (self.xmax - self.xmin) * (W - L - R)
        self.parts.append(f'<rect x="{px - w / 2:.1f}" y="{py:.1f}" width="{w:.1f}" height="{max(self.h - B - py, 0.5):.1f}" rx="3" fill="{COLORS[color]}"/>')
        if label:
            self.parts.append(f'<text x="{px:.1f}" y="{py - 5:.1f}" text-anchor="middle" font-size="12" fill="{INK}">{label}</text>')

    def save(self, folder, name):
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {self.h}" font-family="{FONT}">' + ''.join(self.parts) + '</svg>\n'
        (folder / name).write_text(svg, encoding='utf-8', newline='\n')
        print('wrote', name)


def table(path):
    with open(path, newline='') as file:
        return list(csv.DictReader(file))


def traces(folder):
    """The traces that hold boards and scores, oldest first."""
    result = []
    for path in sorted(Path(folder).glob('trace-*/moves.csv')):
        rows = table(path)
        if rows and 'score_stable' in rows[0]:
            result.append((path.parent.name, rows))
    return result


def single_line_worth(rows):
    """What a single line was worth at each clear of up to three lines that could be measured, by row."""
    stable = [i for i, row in enumerate(rows) if row['score_stable'] == 'true' and row['score']]
    clears = [i for i, row in enumerate(rows) if row['lines'] not in ('', '0')]
    worth = {}
    for i in clears:
        before = max((s for s in stable if s <= i), default=None)
        after = min((s for s in stable if s > i), default=None)
        if before is None or after is None or sum(1 for c in clears if before <= c < after) != 1:
            continue
        lines = int(rows[i]['lines'])
        if lines <= 3:
            blocks = sum(int(row['cells']) for row in rows[before:after])
            worth[i] = (int(rows[after]['score']) - int(rows[before]['score']) - blocks) / WORTH[lines]
    return clears, worth


def lab_charts(lab, out):
    curves = {name: table(lab / f'{name}.csv') for name, _ in STRATEGIES}
    rounds = len(curves['balanced'])
    top = max(int(rows[-1]['mean']) for rows in curves.values())
    step = 50e6 if top > 150e6 else 20e6 if top > 60e6 else 5e6
    chart = Chart('Expected score of a simulated game cut off after so many rounds', rounds, top * 1.05,
                  range(0, rounds + 1, 250), [step * i for i in range(int(top * 1.05 / step) + 1)],
                  'rounds of three pieces', 'million points, mean', yfmt=millions)
    for name, title in STRATEGIES:
        chart.line([(0, 0)] + [(int(row['round']), int(row['mean'])) for row in curves[name][9::10]], name, title)
    chart.save(out, 'score-by-rounds.svg')

    longest = 1.1 * max(float(row['combo_alive'] or 0) for rows in curves.values() for row in rows)
    chart = Chart('How long the combo is after so many rounds', rounds, longest, range(0, rounds + 1, 250),
                  [v for v in range(0, 4001, 100 if longest < 800 else 250) if v <= longest],
                  'rounds of three pieces', 'clearing pieces, mean')
    for name, title in STRATEGIES:
        chart.line([(int(row['round']), float(row['combo_alive'])) for row in curves[name][9::10] if row['combo_alive']], name, title)
    chart.save(out, 'combo-by-rounds.svg')


def trace_charts(folder, out):
    all_traces = traces(folder)

    starts = []
    for _, rows in all_traces:
        clears, worth = single_line_worth(rows)
        at = 0
        while at < len(clears):
            first = worth.get(clears[at])
            if first is None or not 50 <= first <= 58:
                at += 1
                continue
            # A combo from its first clear on, for as long as the worth of a line keeps growing.
            sequence, position = [], 1
            while at < len(clears) and position <= 26:
                value = worth.get(clears[at])
                if value is not None:
                    if sequence and value < sequence[-1][1]:
                        break
                    sequence.append((position, value))
                at += 1
                position += 1
            if len(sequence) >= 4:
                starts.append(sequence)
    chart = Chart('What a single line earns at the start of a combo', 26.5, 2800, range(1, 27, 5), range(0, 2501, 500),
                  'clearing piece of the combo', 'points')
    chart.line([(n, n * (50 if n <= 5 else 75 if n <= 10 else 100)) for n in range(1, 27)], 'balanced', 'the rule')
    chart.dots([point for sequence in starts for point in sequence], 'ink', f'measured, {len(starts)} combos')
    chart.save(out, 'combo-start.svg')

    counts = Counter()
    for path in sorted(Path(folder).glob('trace-*/moves.csv')):
        for row in table(path):
            if row.get('lines') not in ('', None):
                counts[int(row['lines'])] += 1
    clears = sum(count for lines, count in counts.items() if lines)
    points = {lines: counts[lines] * WORTH[lines] for lines in WORTH}
    chart = Chart(f'Lines cleared with one piece, of {clears:,} clearing pieces', 6.6, 80, range(1, 7), range(0, 81, 20),
                  'lines cleared at once', 'percent', xmin=0.4, height=320)
    for lines in WORTH:
        often, share = 100 * counts[lines] / clears, 100 * points[lines] / sum(points.values())
        chart.bar(lines - 0.17, 0.3, often, 'ink', f'{often:.1f}' if often < 10 else f'{often:.0f}')
        chart.bar(lines + 0.17, 0.3, share, 'balanced', f'{share:.1f}' if share < 10 else f'{share:.0f}')
    chart.legend('share of the clearing pieces', 'ink')
    chart.legend('share of the points for clears', 'balanced')
    chart.save(out, 'lines-share.svg')

    chart = Chart('What clearing several lines with one piece is worth', 6.6, 32, range(1, 7), range(0, 31, 10),
                  'lines cleared at once', 'single lines', xmin=0.4, height=300)
    for lines, worth in WORTH.items():
        chart.bar(lines, 0.6, worth, 'balanced', f'{worth:g}')
    chart.save(out, 'lines-worth.svg')

    # The game the newest trace belongs to: back to where the score last started from nothing.
    start = max((i for i, (_, rows) in enumerate(all_traces) if rows[0]['score'] and int(rows[0]['score']) < 1000), default=0)
    game, placed = [], 0
    for _, rows in all_traces[start:]:
        for row in rows:
            placed += 1
            if row['score'] and row['score_stable'] == 'true':
                game.append((placed, int(row['score'])))
    top = game[-1][1]
    step = 10e6 if top > 40e6 else 5e6 if top > 16e6 else 2e6
    chart = Chart('The score of one game on the phone', placed * 1.02, top * 1.05, range(0, placed + 1, 1000 if placed > 4000 else 500),
                  [step * i for i in range(int(top * 1.05 / step) + 1)], 'pieces placed by the bot', 'million points', yfmt=millions)
    chart.line(game[::3], 'balanced')
    chart.save(out, 'real-game.svg')


def deal_charts(deals, lab, out):
    rows = table(deals)
    bounds = [0, 8, 12, 16, 20, 26, 65]
    chart = Chart('Lines the solver clears per round on the same boards', len(bounds) - 0.4, 3.4, [], [0, 1, 2, 3],
                  'blocks on the board when the round begins', 'lines per round', xmin=0.4, height=320)
    for index in range(len(bounds) - 1):
        part = [row for row in rows if bounds[index] <= int(row['filled']) < bounds[index + 1] and row['random_lines_planned']]
        dealt = sum(int(row['lines_planned']) for row in part) / len(part)
        drawn = sum(float(row['random_lines_planned']) for row in part) / len(part)
        chart.bar(index + 1 - 0.17, 0.3, dealt, 'balanced', f'{dealt:.2f}')
        chart.bar(index + 1 + 0.17, 0.3, drawn, 'soft', f'{drawn:.2f}')
        chart.tick(index + 1, f'{bounds[index]}–{bounds[index + 1] - 1}' if index < len(bounds) - 2 else f'{bounds[index]} and more')
    chart.legend('with the pieces the game dealt', 'balanced')
    chart.legend('with pieces drawn at random', 'soft')
    chart.save(out, 'dealt-and-drawn.svg')

    if lab is None:
        return
    simulated = table(lab / 'balanced-combo.csv')
    edges = [int(row['combo_from']) for row in simulated]
    chart = Chart('Blocks on the board by the length of the combo', len(edges) + 0.6, 24, [], range(0, 25, 4),
                  'clearing pieces in the combo when the round begins', 'blocks, mean', xmin=0.4, height=320)
    real = []
    for index, edge in enumerate(edges):
        upper = edges[index + 1] if index + 1 < len(edges) else 10 ** 9
        part = [int(row['filled']) for row in rows if edge <= int(row['combo']) < upper]
        if part:
            real.append((index + 1, sum(part) / len(part)))
        chart.tick(index + 1, f'{edge}–{upper - 1}' if index + 1 < len(edges) else f'{edge} and more')
    chart.line([(index + 1, float(row['filled'])) for index, row in enumerate(simulated)], 'soft', 'simulated, balanced')
    chart.dots(real, 'balanced', 'on the phone')
    chart.line(real, 'balanced')
    chart.save(out, 'filled-by-combo.svg')


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--lab', type=Path)
    parser.add_argument('--traces', type=Path)
    parser.add_argument('--deals', type=Path)
    parser.add_argument('--out', type=Path, default=Path(__file__).resolve().parent.parent / 'docs' / 'charts')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    if args.lab:
        lab_charts(args.lab, args.out)
    if args.traces:
        trace_charts(args.traces, args.out)
    if args.deals:
        deal_charts(args.deals, args.lab, args.out)


if __name__ == '__main__':
    main()
