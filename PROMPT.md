# Prompt: set up Platea Home on my TV

Paste everything below the line into your coding agent (Claude Code, Codex, Gemini CLI…), opened in this repository.
Replace the two values in brackets.

---

Set up Platea Home on my TV and debloat it. My TV is **[brand and model, e.g. Sony BRAVIA KD-55XH9096]** and its IP
address is **[192.168.1.50]**. Reply to me in the language I am writing in.

Follow `AGENTS.md` in this repository exactly, section "Job A: set up a TV". In short:

1. Connect over ADB and check the TV (model, Android version, current Home, RAM, storage, whether it is on Wi-Fi
   or Ethernet, which apps I really use). Change nothing yet.
2. Find my exact model in CleanYourTV's `examples/` and tell me how close the match is. Then show me the plan, and
   ask me which preinstalled apps and features I use (voice assistant, Chromecast, live TV, HbbTV, recording…), which
   6 apps I want in the dock and which palette I like. Wait for my answers.
3. Debloat with `./platea-home.sh debloat <list>`, using CleanYourTV's list for my model with the apps I keep
   commented out, after a backup. Never uninstall anything, and never disable core Android or TV packages.
4. Write `config/<my-tv>.json` with my dock, my hidden apps and the live TV app, then install with
   `CONFIG=config/<my-tv>.json SIPARIO=1 ./platea-home.sh install`. Never change Home or disable launchers by hand.
5. Check it with screenshots and reboot once. Then ask me to press the real remote buttons (Home, hold OK, Input,
   the gear). Finally, tell me how to undo everything, remind me to back up `keys/`, and ask whether to turn ADB debugging off.
