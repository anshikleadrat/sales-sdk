# AI Query SDK — Chrome extension

A Manifest V3 extension that asks questions about business records through an AI Query SDK
deployment, either from the toolbar popup or from a panel injected into pages that mark up
their records.

## Load it

1. Open `chrome://extensions`, enable **Developer mode**, choose **Load unpacked** and select
   this `extension/` directory.
2. Copy the extension id Chrome assigns, and allow it in the deployment's configuration:

   ```yaml
   ai-sdk:
     security:
       allowed-origins:
         - chrome-extension://<the id>
   ```

   Without this, the browser blocks the cross-origin call to `/ai-sdk/query`.
3. Open the extension's **Settings**, enter the deployment URL (the base path ending in
   `/ai-sdk`) and the admin password, then sign in.

## How it works

- `background.js` is the only place that talks to the deployment. It holds the JWT and its
  expiry in `chrome.storage.local`; the admin password is sent once to `/ai-sdk/auth/token`
  and never stored. On a 401 the token is dropped and the UI asks you to sign in again.
- `popup.html` lists the entities the deployment has enabled, pre-fills the entity and id
  from the active tab when the page exposes them, and renders the answer plus the records
  that were retrieved.
- `content.js` looks for `data-ai-sdk-entity` / `data-ai-sdk-id` attributes on the page:

  ```html
  <div data-ai-sdk-entity="Client" data-ai-sdk-id="4521"> … </div>
  ```

  When it finds them and the extension is signed in, it mounts a launcher in the corner of
  the page. The panel renders in a shadow DOM, so the host page's styles cannot affect it
  and it cannot affect them.

## Packaging

The directory loads unpacked as-is. To publish through the Chrome Web Store, zip the
directory contents (not the folder), add icons to `manifest.json`, and narrow
`host_permissions` from `http://*/*` and `https://*/*` to the origins your deployment
actually runs on.
