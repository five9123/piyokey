#!/usr/bin/env python3
"""Compile the production Game Center service with controlled framework adapters.

Only Combine/GameKit/UIKit imports are replaced; production methods and timing are
unchanged. Framework double callbacks control failures, delays and release lists.
This verifies app behavior, not Apple's server receipt on a real device.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'tools/fixtures/game_center_delivery'

def main():
    source = ROOT / 'ios/Hanco/Hanco/Core/GameCenter/GameCenterService.swift'
    with tempfile.TemporaryDirectory(prefix='piyokey-delivery-') as temp:
        directory = Path(temp)
        service = directory / 'Service.swift'
        service.write_text('\n'.join(line for line in source.read_text().splitlines()
            if line not in {'import Combine', 'import GameKit', 'import UIKit'}))
        binary = directory / 'delivery'
        subprocess.run(['xcrun', 'swiftc', '-swift-version', '5', '-parse-as-library',
            '-module-cache-path', str(directory / 'cache'),
            str(FIXTURES / 'FrameworkDoubles.swift'), str(service),
            str(source.with_name('GameCenterRankings.swift')),
            str(FIXTURES / 'Scenarios.swift'), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True)

if __name__ == '__main__':
    main()
