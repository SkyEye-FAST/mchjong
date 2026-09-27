"""Serve a separately installed Mortal three-player package over mjai lines."""

import argparse
import json
import os
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("package_root", type=Path)
    args = parser.parse_args()
    root = args.package_root.resolve(strict=True)
    if not (root / "mjai_bot" / "mortal3p" / "mortal.pth").is_file():
        parser.error("package_root must contain mjai_bot/mortal3p/mortal.pth")

    sys.path.insert(0, str(root))
    dll_handles = []
    if os.name == "nt":
        for directory in (root, root / "torch" / "lib"):
            if directory.is_dir():
                dll_handles.append(os.add_dll_directory(str(directory)))

    import torch
    from mjai_bot.mortal3p import bot, model

    torch.set_num_threads(1)
    client = bot.Bot()
    model.ot_settings["online"] = False
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    for line in sys.stdin:
        event = json.loads(line)
        action = client.react(json.dumps([event], separators=(",", ":")))
        if event.get("can_act"):
            sys.stdout.write(action + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
