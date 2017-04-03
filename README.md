# rx-workshop

Hello!
Welcome to the workshop. I hope that you will enjoy it. 

## Links

* Main site for documentation: [http://reactivex.io/](http://reactivex.io/)
* Marble diagrams [http://rxmarbles.com/](http://rxmarbles.com/)
* 101 Rx examples [http://rxwiki.wikidot.com/101samples](http://rxwiki.wikidot.com/101samples)
* Rx Operators decision tree [http://reactivex.io/documentation/operators.html](http://reactivex.io/documentation/operators.html) (scroll down)

## First step
Clone this repo!

## Getting started

### Starting the UI
Tested with `Chromium 47.0.2526.73 Ubuntu 15.10 (64-bit)`.

In order to start the UI, open [ws-ui/index.html](ws-ui/index.html) (locally) in Chrome (or another browser if you dare :) ). 
The UI will try to establish a connection to a backend periodically. The connection light will turn green when it has established the connection.
 It assumes the address `ws://localhost:4739`. Check the console for errors (Ctrl+Shift+J in Chrome).

#### Starting the .NET Backend
Open solution in Visual Studio 2015. Press play.

Start editing in Handler.cs to implement the necessary functionality.

#### Starting the Java backend
Tested with `OpenJDK 1.8.0_66-internal` and `Maven 3.2.5`.

Open the maven project [ws-java](ws-java/) in your favourite editor and run the 
main method in the `Server` class.
 
If you really want to run it from the command line, run `mvn exec:java` to start the server.

Start editing in EmptyHandler.java to implement the necessary functionality.

When the UI page connects you should see the app logging events to stdout:

```
Server started
connect from /0:0:0:0:0:0:0:1:50200
message from /0:0:0:0:0:0:0:1:50200: {"type":"query.input","text":"a"}
message from /0:0:0:0:0:0:0:1:50200: {"type":"query.input","text":"ap"}
message from /0:0:0:0:0:0:0:1:50200: {"type":"query.input","text":"apa"}
```

#### Starting the Node backend
Tested with `Node.JS 5.5.0`.

`cd` into [ws-node](ws-node/) and run `npm install` followed by `node index.js`. Refresh the UI page and type into the text box.
You should see the app logging events to stdout:

```
connection from file://
msg: {"type":"utf8","utf8Data":"{\"type\":\"query.input\",\"text\":\"a\"}"}
msg: {"type":"utf8","utf8Data":"{\"type\":\"query.input\",\"text\":\"ap\"}"}
msg: {"type":"utf8","utf8Data":"{\"type\":\"query.input\",\"text\":\"apa\"}"}
```

Open `index.js` in your favourite editor and edit the `Handler` function to implement the functionality.

#### Making your own backend
Make a server which accepts WS connections on ws://localhost:4739. Accept text messages like these:
```
{"type":"query.input","text":"a"}
{"type":"enter.press"}
{"type":"go.click"}
{"type":"instant.enable","value":true}
```

and answer with messages like these:
```
{"links":["http://a.com","http://b.com"]}
```

Convert incoming messages to `Observable`s: one type of `Observable` per type of incoming message and one type of `Observable`
for outgoing messages. Implement the functionality by building rx pipelines with the incoming `Observable`s and pushing the result to
the outgoing `Observable`. A simple way of bridging is to use a [Subject](http://reactivex.io/documentation/subject.html). 

Also add a HTTP client to use with Rx on the backend.

See the existing impls for inspiration.

**Note**: Don't forget that you will get multiple clients if you reload the page!

## The assignments

1. Update the backend status panel in the UI with the text "ready" when the client connects to the backend. 
   Do this by manually pushing a message to the status observer by calling the "onNext" method on it.
2. Also manually push a placeholder URL to the result list on connect. This can be any URL, like "www.hello.com" or whatever.
   The result list is represented by the "links" observer.
3. Instead of pushing the placeholder URL straight away on connect, simulate a search by delaying the push of the 
   placeholder URL until text is entered in the search field. The "queryInput" observable emits the search input whenever it changes. 
   Use these events to trigger the pushing of your placeholder URL. 
   Use "map" to transform the search input observable to an observable which emits your placeholder URL whenever the search input changes.
   Connect your placeholder URL observable to the "links" observer.  
   The connection can be made by registering the observer as a subscriber to the observable, using the "subscribe" method.
4. Instead of pushing just the placeholder URL, emit a URL based on the text in the search field. So if you enter enter the text "test" in the search field, 
    you should respond by updating the result list with a single element with a single URI, for example "www.test.com".
5. Push status messages before and after the simulated search result are produced. 
   Push "searching for 'term'" just before and "search done" just after. To push the 
   first message, add a second observer to the search input observable by calling "map" on the search input observable a second time.
   Your map should transform the search input to a status message. Now connect the resulting observable to the status observer.  
   The second status message can be produced in a very similar way by hooking into the appropriate observable just before the result is pushed to the client.
   Add a delay of 300ms to the mock search results to see the effects.
6. Preparation for a real search. Instead of producing the mock list with a simple map in excercise 4,
   your are to wrap the list in an observable. This will make the API compatible with the concurrently running search in the next step.
   To do this, make a method with the signature `Observable<List<String>> search(String searchTerm)`.
   Use `Observable.just` to construct an observable from your mock list and return
   it from the method. Now try to hook a call to this new method into the chain where the old `map` call is. On you first try, you most likely end up
   with an observable of observables. How can you get away from this situation?   
6. Now for the real searching. In your handler there is a `duckDuckGoClient` member. Call its `searchRelated` method instead of your mock search. 

## Bonus assignments

7. "Instant search" happens too quickly after keypresses and you're flooding the server. Make sure that the search is only triggered 500 ms after the user stops typing. Use `debounce`.    
8. Delay the search until the client presses the "go" button. Use the `goClicks` observable and [sample](http://reactivex.io/RxJava/javadoc/rx/Observable.html#sample(rx.Observable))
9. Also trigger the search when the user presses the enter key in the search field.
10. Re-enable instant search when the "instant search" checkbox is checked. One way of doing this is to pair up the latest checkbox state with the latest search phrase whenever either one of them changes. 
   Then you only let the search phrases pass if they are paired with a `checked` boolean flag. Can you find the operators?
11. Support multiple search phrases separated by comma (","). So if you search for "grass, spring", you should perform two HTTP requests against the duckduckgo API and combine the results before showing them to the user.
12. Use two search engines. Apart from duckduckgo, you are to query wikipedia [https://www.mediawiki.org/wiki/API:Search](https://www.mediawiki.org/wiki/API:Search).
13. Build a new status indicator in the UI for ongoing backend requests. It should be able to indicate outstanding duckduckgo requests as well outstanding wikipedia requests.
    b. Interleave the search results - ie [duckduckgo #1, wikipedia #1, duckduckgo #1, wikipedia #2 ... ].
14. You're fast, make up your own assignment!
