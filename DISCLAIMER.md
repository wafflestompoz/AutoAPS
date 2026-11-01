# Safety notice and disclaimer

The app shows this same notice on first launch and requires the user to accept it before the car screen shows any data. If you change it, also update `disclaimer_body` in `app/src/main/res/values/strings.xml` and bump `Settings.DISCLAIMER_VERSION` so existing users accept the new text.

---

AutoAPS is **not a medical device** and has not been cleared or approved by the FDA, the EU MDR notified bodies, or any other regulator. It shows a copy of data from your own Nightscout site. That data can be delayed, missing or wrong.

- **Never make treatment decisions** (insulin, carbohydrates, or anything else) from AutoAPS. Confirm with your CGM receiver or app, or with a fingerstick.
- **AutoAPS has no alarms.** Keep your CGM and loop alerts switched on.
- Readings 15 minutes or older are marked **STALE**.
- **Do not look at or set up AutoAPS while driving.** If your blood sugar may be low, stop driving safely and treat it.
- AutoAPS **only reads data.** It cannot dose insulin, change settings, or control any pump, CGM or loop.

AutoAPS is an independent open-source project. It is not affiliated with or endorsed by the Nightscout Foundation, AndroidAPS, Dexcom, Abbott, Medtronic, Insulet, Tandem, Google, or any other company. All product names are trademarks of their owners.

AutoAPS is provided "as is", without warranty of any kind, express or implied. You use it entirely at your own risk. In no event shall the authors be liable for any claim, damages or other liability arising from its use.
