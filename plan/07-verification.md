# Verification checklist

- **Phase 1**: run the RC/Celebrity scraper module locally against a real sailing (ship + date you can look up manually on royalcaribbean.com) and confirm the returned fare matches what the site shows.
- **Phase 2**: run the Android app against a Firebase test project, manually write a test `priceSnapshots` doc below `farePaid` via the Firestore console, confirm an alert doc is generated and a push notification arrives on a real/emulated device.
- **Phase 3**: repeat the Phase 1 manual-fare-match check for each newly added line (Carnival, Princess, NCL).
- **Phase 4**: install via Play Store internal testing track on a real device before promoting to production.
