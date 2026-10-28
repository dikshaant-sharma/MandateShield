# Getting Started

## Local configuration

Copy `.env.example` to `.env` and fill in your local Gemini and MongoDB Atlas credentials. Spring loads `.env` for local runs; `.env` is ignored so secrets stay out of source control. The configured test values in the existing local `.env` should be rotated before sharing or presenting this checkout.

## Optional Gemini merchant fallback

Core features run without Gemini. To enable the Gemini fallback, set `GOOGLE_GENAI_API_KEY` and `MANDATESHIELD_CHAT_MODEL=google-genai` in the local `.env` file.

Tier 2 stores its heuristic result immediately. When the result is `UNKNOWN` at or below the configured confidence threshold, the app sends only the raw descriptor to Gemini asynchronously. A valid response updates exact matching records for that user and creates a user-scoped Tier 1 pattern. An explicitly confirmed merchant can optionally be added to the shared alias dictionary; it stores a one-way descriptor fingerprint and merchant label, never transaction data. The model defaults to `gemini-3.8-flash`.

## Pinecone merchant matching

The resolver checks user and shared exact matches first, then queries the configured Pinecone index, then uses local descriptor heuristics. The Pinecone API key is loaded from the ignored local `.env` file. Set `PINECONE_INDEX_NAME` to an existing index with 384-dimensional vectors; optionally set `PINECONE_INDEX_HOST` to skip index discovery. The app creates local character n-gram vectors and stores them with merchant labels; raw descriptors are not stored as metadata. If Pinecone is unavailable or the configured index is missing, resolution continues through local heuristics and the Gemini fallback.

Trial entries can include a trial end date and expected first charge. The dashboard raises an alert when that date is within 48 hours. Use **Confirm used recently** on a recurring payment to record service activity; the zombie tracker flags active payments after 90 days without confirmation.

## Synthetic evaluation

From the signed-in dashboard, choose **Load synthetic sample**. The generated history is marked synthetic and scoped to your account. The summary reports merchant-label accuracy and detection of recurring groups, a price increase, trial conversion, and an annual renewal. CSV import uses the downloadable template on the Transactions screen.
