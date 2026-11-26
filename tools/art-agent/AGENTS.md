# Art agent

- Never change the model unless asked. A model 404 means fix `GOOGLE_CLOUD_LOCATION` (e.g. `global`), not the model name.
- ADK tools: import the tool instance, not the module (`from google.adk.tools.load_web_page import load_web_page`).
- `agents-cli deploy` only after Andy says yes.
