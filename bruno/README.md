# LimeHD Bruno Collection

Open the `bruno/` folder in Bruno and select the `LimeHD` environment.

Requests:

- `01 - LimeHD auth` — gets `public_key`.
- `02 - LimeHD config` — gets LimeHD regions and current auto region.
- `03 - LimeHD playlist` — uses `region={{region}}`; `region=0` is auto, `region=77` is Moscow.
- `04 - LimeHD epg` — uses `id={{id}}` and `region={{region}}`.

The playlist request needs `payload`. Generate it as Base64 RSA-encrypted payload exactly as in `LimeHdClient.kt`, then paste it into the `payload` variable or into the form body.
