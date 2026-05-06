"""Generate an Azure OpenAI code review for a GitHub PR diff.

Reads the unified PR diff from stdin (piped from `gh api`), calls the
configured Azure OpenAI deployment with a code-review system prompt, and
writes a base64-encoded markdown comment body to $GITHUB_OUTPUT under the
key `comment_result`. The composite action `comment_ai_review` then posts
or updates a single PR comment keyed by the header marker.
"""

import base64
import os
import sys

from openai import AzureOpenAI

# --- Inputs -----------------------------------------------------------------
repo = sys.argv[1]                        # e.g. IABTechLab/uid2-admin
pr_number = sys.argv[2]                   # e.g. 1234
workflow_run_link = sys.argv[3]           # link back to the Actions run

# The marker used by comment_ai_review to find/update/delete its own comment.
HEADER_MARKER = "## Azure OpenAI Code Review"

# Hard cap on diff size sent to the model. Diffs larger than this are
# truncated; the comment notes the truncation. ~60k chars ≈ 15k tokens, well
# inside gpt-5 input limits while leaving headroom for the system prompt.
MAX_DIFF_CHARS = 60000

# --- Read diff --------------------------------------------------------------
def write_no_review(reason: str) -> None:
    """Tell the composite action there is no review, so any prior comment is removed."""
    print(f"No review produced: {reason}", file=sys.stderr)
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write("has_review=False\n")
            f.write("comment_result=\n")


diff = sys.stdin.read()
if not diff.strip():
    write_no_review("empty diff")
    sys.exit(0)

truncated = False
if len(diff) > MAX_DIFF_CHARS:
    diff = diff[:MAX_DIFF_CHARS]
    truncated = True

# --- Azure OpenAI -----------------------------------------------------------
endpoint = os.getenv("AZURE_OPENAI_ENDPOINT", "")
deployment = os.getenv("AZURE_OPENAI_DEPLOYMENT", "gpt-5")
api_key = os.getenv("AZURE_OPENAI_API_KEY", "")

if not api_key:
    write_no_review("AZURE_OPENAI_API_KEY is not set")
    sys.exit(0)

if not endpoint:
    write_no_review("AZURE_OPENAI_ENDPOINT is not set")
    sys.exit(0)

client = AzureOpenAI(
    azure_endpoint=endpoint,
    api_key=api_key,
    api_version="2024-12-01-preview",
)

system_prompt = """You are a senior software engineer reviewing a pull request for the UID2/EUID project. The codebase is primarily Java (Maven) with some Python tooling.

Review the unified diff below and produce a focused, actionable review. Concentrate on:

1. **Correctness** — logic errors, off-by-one, null handling, concurrency, resource leaks.
2. **Security** — input validation, secret/credential handling, SQL/command injection, OWASP top 10, unsafe deserialization.
3. **Error handling** — swallowed exceptions, missing logging, retry/backoff, partial failure modes.
4. **Test coverage** — note untested code paths the diff introduces, especially around new branches, error handling, and edge cases.
5. **API/contract changes** — anything that breaks consumers, changes wire format, or alters persisted data shape.

Format the review as GitHub-flavoured markdown:

- Start with a one-paragraph **Summary** of what the PR does.
- Then a **Findings** section, grouped under bold severity headers: `### 🔴 Blocking`, `### 🟡 Suggestions`, `### 🟢 Nits`. Omit any header with no findings.
- Each finding: a short bold title, one or two sentences explaining the issue, and a `file.ext:line` reference where possible. Quote no more than 3 lines of code per finding.

Be terse. Prefer fewer high-signal findings over an exhaustive list. If the diff looks fine, say so plainly under a single **Summary** and skip Findings. Do not restate what the diff already shows.
"""

completion = client.chat.completions.create(
    model=deployment,
    messages=[
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": diff},
    ],
    max_completion_tokens=2000,
    frequency_penalty=0,
    presence_penalty=0,
    stream=False,
)

review_body = completion.choices[0].message.content or "_(no review produced)_"

# --- Compose comment --------------------------------------------------------
truncation_note = (
    f"\n> _Note: the diff exceeded {MAX_DIFF_CHARS:,} characters and was truncated before review._\n"
    if truncated else ""
)

comment = f"""{HEADER_MARKER}

_Powered by Azure OpenAI ({deployment}). This is an automated review intended as a starting point — human review is still required._
{truncation_note}
{review_body}

---

<sub>Workflow run: {workflow_run_link} · Repo: {repo} · PR: #{pr_number}</sub>
"""

print(comment)

encoded = base64.b64encode(comment.encode("utf-8")).decode("utf-8")
github_output = os.environ.get("GITHUB_OUTPUT")
if github_output:
    with open(github_output, "a") as f:
        f.write(f"comment_result={encoded}\n")
        f.write("has_review=True\n")
else:
    print("GITHUB_OUTPUT not set; comment will not be posted.", file=sys.stderr)
