#!/usr/bin/env python3
"""Counts the pieces the game dealt, by how full the board was, and writes them into PieceCatalog.

    python tools/count_pieces.py captures

`captures` is the folder with the phone's `trace-*` folders. Only shapes that are in the catalog
already are counted; what else the traces hold, misread pieces mostly, is reported.

Needs nothing but Python 3.
"""
import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

CATALOG = Path(__file__).resolve().parent.parent / 'app/src/main/java/io/github/andreasscholz/bbbot/game/PieceCatalog.kt'
# From how many blocks on a board counts as fuller; the same as FULLER in the catalog.
FULLER = [0, 10, 15, 20]


def shape(mask):
    rows = []
    for row in range(8):
        if mask >> (row * 8) == 0:
            break
        rows.append((mask >> (row * 8)) & 0xFF)
    width = max(bits.bit_length() for bits in rows)
    return tuple(''.join('X' if bits >> col & 1 else '.' for col in range(width)) for bits in rows)


counts = defaultdict(lambda: [0] * len(FULLER))
rounds = 0
for path in sorted(Path(sys.argv[1]).glob('trace-*/moves.csv')):
    with open(path, newline='') as file:
        rows = list(csv.DictReader(file))
    if not rows or 'offered' not in rows[0]:
        continue
    seen = set()
    for row in rows:
        offered = [piece for piece in row['offered'].split(';') if piece]
        if row['round'] in seen or len(offered) != 3:
            continue
        seen.add(row['round'])
        rounds += 1
        filled = bin(int(row['board'], 16)).count('1')
        level = max(index for index, start in enumerate(FULLER) if filled >= start)
        for piece in offered:
            counts[shape(int(piece.split(':')[1], 16))][level] += 1

text = CATALOG.read_text(encoding='utf-8')
pattern = re.compile(r'(Piece\.of\(([^)]*)\) to intArrayOf\()[^)]*\)')
known = 0


def replace(match):
    global known
    rows = tuple(re.findall(r'"([X.]+)"', match.group(2)))
    known += sum(counts[rows])
    return match.group(1) + ', '.join(str(count) for count in counts[rows]) + ')'


text = pattern.sub(replace, text)
text = re.sub(r'counted in traces of real games: \d+ pieces in\n \* \d+ rounds', f'counted in traces of real games: {known} pieces in\n * {rounds} rounds', text)
CATALOG.write_text(text, encoding='utf-8', newline='\n')
print(f'{rounds} rounds, {rounds * 3} pieces, {known} of them shapes of the catalog')
