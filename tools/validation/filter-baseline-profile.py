"""Filter an unminified ART profile to app families observed in an optimized journey.

Inputs are code descriptors only (adb cmd package dump-profiles --dump-classes-and-methods).
Never ship R8-transformed descriptors as source rules. Library profiles are merged by AGP.
This filter is not a coverage/performance assertion; measure the packaged profile separately.
"""
import argparse
import pathlib
import re


def family(rule):
    return re.sub(r"^.*?L", "L", rule.split(";")[0]).split("$")[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--reference", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    namespace = "Lcom/imsx3d/classy/"
    observed = {family(line) for line in args.reference.read_text().splitlines()
                if namespace in line.split(";")[0]}
    rules = sorted({line for line in args.source.read_text().splitlines()
                    if namespace in line.split(";")[0]
                    and "ExternalSynthetic" not in line and family(line) in observed})
    if not rules:
        raise SystemExit("No app rules matched; refusing to replace a profile with an empty file.")
    args.output.write_text(
        "# Generated from unminified runtime profiles; see docs/engineering/PERFORMANCE_20261014.md.\n"
        + "\n".join(rules) + "\n", encoding="utf-8")
    print(f"Wrote {len(rules)} rules")


if __name__ == "__main__":
    main()
