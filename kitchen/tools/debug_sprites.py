import json
from pathlib import Path

# Read and execute the gen_assets.py up to main
with open('E:/wt/kitchen-recipes-a/kitchen/tools/gen_assets.py', 'r') as f:
    content = f.read()

# Execute up to main()
exec(content.split('def main():')[0])

# Now check all sprites
for name, rows in SPRITES.items():
    if len(rows) != 16:
        print(f'{name}: {len(rows)} rows')
    for i, row in enumerate(rows):
        if len(row) != 16:
            print(f'{name} row {i}: {len(row)} cols - {row}')