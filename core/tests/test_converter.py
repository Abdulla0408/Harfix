import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from yangi_alifbo import convert  # noqa: E402

CASES = json.loads((pathlib.Path(__file__).parent / "cases.json").read_text(encoding="utf-8"))


def test_cases():
    failures = [(src, exp, convert(src)) for src, exp in CASES if convert(src) != exp]
    assert not failures, "\n".join(f"{s!r}: expected {e!r}, got {g!r}" for s, e, g in failures)


if __name__ == "__main__":
    fails = 0
    for src, exp in CASES:
        got = convert(src)
        if got != exp:
            fails += 1
            print(f"FAIL {src!r}: expected {exp!r}, got {got!r}")
    print(f"{len(CASES) - fails}/{len(CASES)} passed")
    sys.exit(1 if fails else 0)
