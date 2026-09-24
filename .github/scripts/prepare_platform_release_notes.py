import os
import re
import sys
from pathlib import Path
from urllib.parse import urljoin, urlsplit


def normalize_label(label: str) -> str:
    return " ".join(label.split()).casefold()


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: prepare_platform_release_notes.py VERSION")

    version = sys.argv[1]
    repository = os.environ.get("GITHUB_REPOSITORY")
    if not repository:
        raise SystemExit("GITHUB_REPOSITORY is required")

    changelog = Path("CHANGELOG.md").read_text(encoding="utf-8")
    notes = Path("release-notes.md").read_text(encoding="utf-8")
    definitions = {
        normalize_label(match.group(1)): match.group(2) or match.group(3)
        for match in re.finditer(
            r"^\[([^\]\r\n]+)\]:[ \t]*(?:<([^>]+)>|(\S+))(?:[ \t]+.*)?$",
            changelog,
            re.MULTILINE,
        )
    }

    def resolve_reference(match: re.Match[str]) -> str:
        label, explicit_label = match.group(1), match.group(2)
        reference = normalize_label(explicit_label or label)
        destination = definitions.get(reference)
        if destination is None:
            return match.group(0)
        return f"[{label}]({destination})"

    notes = re.sub(
        r"(?<!!)\[([^\]\r\n]+)\](?:\[([^\]\r\n]*)\])?(?!\s*\()",
        resolve_reference,
        notes,
    )

    repository_url = f"https://github.com/{repository}"
    release_base = f"{repository_url}/blob/{version}/"

    def resolve_destination(match: re.Match[str]) -> str:
        label, raw_destination, title = match.groups()
        destination = (
            raw_destination[1:-1]
            if raw_destination.startswith("<")
            else raw_destination
        )
        parsed = urlsplit(destination)
        if parsed.scheme:
            absolute = destination
        elif parsed.netloc:
            absolute = f"https:{destination}"
        elif destination.startswith("#"):
            absolute = f"{release_base}CHANGELOG.md{destination}"
        else:
            absolute = urljoin(release_base, destination)
        return f"[{label}]({absolute}{title or ''})"

    notes = re.sub(
        r"(?<!!)\[([^\]\r\n]*)\]\((<[^>]+>|[^)\s]+)(\s+[^)]*)?\)",
        resolve_destination,
        notes,
    )

    for match in re.finditer(
        r"(?<!!)\[[^\]\r\n]*\]\((<[^>]+>|[^)\s]+)(?:\s+[^)]*)?\)",
        notes,
    ):
        destination = match.group(1)
        if destination.startswith("<"):
            destination = destination[1:-1]
        if not urlsplit(destination).scheme:
            raise SystemExit(
                f"Platform release notes contain a relative link: {destination}"
            )

    with Path("platform-release-notes.md").open(
        "w",
        encoding="utf-8",
        newline="\n",
    ) as platform_notes:
        platform_notes.write(notes.rstrip() + "\n")


if __name__ == "__main__":
    main()
