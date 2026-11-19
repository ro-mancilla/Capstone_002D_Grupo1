#!/usr/bin/env python3
"""cover.py <test-path> <story numbers, e.g. 11-18,20>: marks stories covered in docs/e2e/COVERAGE.md."""
import re, sys
path, spec = sys.argv[1], sys.argv[2]
nums = set()
for part in spec.split(','):
    a, _, b = part.partition('-'); nums.update(range(int(a), int(b or a) + 1))
f = 'docs/e2e/COVERAGE.md'; out = []
for line in open(f).read().splitlines():
    m = re.match(r'\| (\d+) \| (.*) \| (.*) \|$', line)
    if m and int(m.group(1)) in nums:
        cur = m.group(3)
        cell = f'`{path}`' if cur == 'not yet' else (cur if f'`{path}`' in cur else f'{cur}, `{path}`')
        line = f'| {m.group(1)} | {m.group(2)} | {cell} |'
    out.append(line)
open(f, 'w').write('\n'.join(out) + '\n')
