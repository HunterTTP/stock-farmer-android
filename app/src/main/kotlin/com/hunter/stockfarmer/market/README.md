# Stock quotes

Keep one `StockQuoteProvider` per market session and inject it as the core
`com.hunter.stockfarmer.core.market.StockQuoteProvider`. Call its suspending `quote(ticker)` from
the session's coroutine scope; HTTP runs on `Dispatchers.IO`. Apply returned `StockQuote` values
through the existing engine stock actions on the game state's owning thread.

The provider normalizes symbols, uses the iOS Yahoo chart URL and User-Agent, and sets ten-second
connect/read timeouts. Successful quotes are cached in memory per symbol for the core's five-minute
pull spacing, measured from fetch start rather than Yahoo's market timestamp. A clock rollback
allows a fresh fetch. Failed refreshes throw rather than silently returning an expired price.

Display `StockQuoteException.error.message` for failures. Cancellation propagates to the caller.
The existing engine rules still control when owned holdings may be refreshed. This provider does
not implement market menu selection, loading state, batch error messages, or automatic refresh.
