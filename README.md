*********************************************
*  LLM Client Configuration                 *
*  Provide parameters via command line or   *
*  enter them interactively below           *
*********************************************

[-h] to show this message.

Parameters:
1) Sample limit (default: null) [-sl]
2) Thread count for parallelism (default: 1) [-t]
3) Delay between requests in ms (default: 0) [-d]
4) GPU configuration string (default: null) [-g]
5) Model name (required) [-m]
6) Model URL (required) [-u]
7) API key (default: null) [-k]
8) Context size (required) [-cs]
9) Path to tokenizer json (default: Qwen2.5-Coder-14B tokenizer) [-tk]
10) Path to dataset json (default: repoEval line-level) [-ds]
11) Use mocked model (default: false) [-mck]


Example:
-sl 500 -t 2 -d 300 -g 4090 -m qwen2.5-coder-7b -u http://URL:7777/v1/completions -cs 2048
