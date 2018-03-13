# JavaHttpClient
JavaHttpClient

#HTTPClient Example
Here are some simple examples of using the HTTPClient call.

##Simple Blocking Request
```java
    HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    try {
      HTTPResponseData response = httpClient.request(new URL("http://www.google.com"));
      System.out.println(response.getResponse());
      System.out.println(response.getBodyAsString());
    } catch(HTTPParsingException e) {
      e.printStackTrace();
    }
```


##Simple Async Request
```java
    HTTPClient httpClient = new HTTPClient();
    httpClient.start();
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHost("www.google.com").setPort(80).setSSL(false);
    ListenableFuture<HTTPResponseData> lfr1 = httpClient.requestAsync(hrb.buildClientHTTPRequest());
    ListenableFuture<HTTPResponseData> lfr2 = httpClient.requestAsync(hrb.buildClientHTTPRequest());
    ListenableFuture<HTTPResponseData> lfr3 = httpClient.requestAsync(hrb.buildClientHTTPRequest());
    FutureCallback<HTTPResponseData> fc = new FutureCallback<HTTPResponseData>() {
      @Override
      public void handleResult(HTTPResponseData result) {
        System.out.println(result.getResponse());
        System.out.println(result.getBodyAsString());
      }
      @Override
      public void handleFailure(Throwable t) {
        System.out.println("ERROR:");
        t.printStackTrace();
      }};

    lfr1.addCallback(fc);
    lfr2.addCallback(fc);
    lfr3.addCallback(fc);
    //fyi, all this is async
      
```

#HTTPStreamClient Examples
Here are some examples using the HTTPStreamClient

##Simple Incoming Data Request
```java
    ThreadedSocketExecuter TSE = new ThreadedSocketExecuter();
    TSE.start();
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHost("www.google.com").setPort(80);
    HTTPStreamClient hsc = new HTTPStreamClient(TSE, "www.google.com", 80);
    hsc.setHTTPStreamReader(new HTTPStreamReader() {
      MergedByteBuffers mbb = new MergedByteBuffers();

      @Override
      public void handle(ByteBuffer bb) {
        mbb.add(bb);
        System.out.println("Data output:\n"+mbb.getAsString(mbb.remaining()));
      }});

    System.out.println(hrb.build().toString());
    
    ListenableFuture<HTTPResponse> lfr = hsc.writeRequest(hrb.build());

    lfr.addCallback(new FutureCallback<HTTPResponse>() {
      @Override
      public void handleResult(HTTPResponse result) {
        System.out.println(result.toString());
      }
      @Override
      public void handleFailure(Throwable t) {
        System.out.println("ERROR:");
        t.printStackTrace();
      }});
    hsc.connect();
    //fyi, all this is async
```

##Chunked Outgoing Data
```java
    ThreadedSocketExecuter TSE = new ThreadedSocketExecuter();
    TSE.start();
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHost("hosttopost.none").setPort(80)
        .setHeader(HTTPConstants.HTTP_KEY_TRANSFER_ENCODING, "chunked")
        .setRequestType(RequestType.POST);
    final HTTPStreamClient hsc = new HTTPStreamClient(TSE, "hosttopost.none", 80);
    hsc.setHTTPStreamReader(new HTTPStreamReader() {
      MergedByteBuffers mbb = new MergedByteBuffers();

      @Override
      public void handle(ByteBuffer bb) {
        mbb.add(bb);
        System.out.println("Data output:\n"+mbb.getAsString(mbb.remaining()));
      }});

    System.out.println(hrb.build().toString());
    ListenableFuture<HTTPResponse> lfr = hsc.writeRequest(hrb.build());
    lfr.addCallback(new FutureCallback<HTTPResponse>() {
      @Override
      public void handleResult(HTTPResponse result) {
        System.out.println(result.getHeaders());
      }
      @Override
      public void handleFailure(Throwable t) {
        System.out.println("ERROR:");
        t.printStackTrace();
      }});

    hsc.write(ByteBuffer.wrap("EACH".getBytes()));
    hsc.write(ByteBuffer.wrap("WRITE".getBytes()));
    hsc.write(ByteBuffer.wrap("IS".getBytes()));
    hsc.write(ByteBuffer.wrap("A".getBytes()));
    hsc.write(ByteBuffer.wrap("CHUNK".getBytes()));
    //Ends the chunks.
    hsc.write(ByteBuffer.allocate(0));
    //make sure they responded.
    lfr.get();
    lfr = hsc.writeRequest(hrb.build());
    lfr.addCallback(new FutureCallback<HTTPResponse>() {
      @Override
      public void handleResult(HTTPResponse result) {
        System.out.println(result.getHeaders());
      }
      @Override
      public void handleFailure(Throwable t) {
        System.out.println("ERROR:");
        t.printStackTrace();
      }});
    hsc.write(ByteBuffer.wrap("NOW WE POST AGAIN".getBytes()));
    hsc.write(ByteBuffer.wrap(new byte[0]));
    lfr.get();
```

##WebSocket Client Example
```java
    final SettableListenableFuture<Boolean> slf = new SettableListenableFuture<Boolean>(); //Used to block till response since everything is async.
    ThreadedSocketExecuter TSE = new ThreadedSocketExecuter();
    TSE.start();
    //Can use ws or wss for ssl, you can also call enableSSL with an SSLEngine to use, default is full trust engine, no cert validation!
    final WebSocketClient wsc = new WebSocketClient(TSE, new URI("ws://echo.websocket.org")); 
    wsc.setWebSocketDataReader(new WebSocketDataReader() {
      @Override
      public void onData(ByteBuffer bb) {
        MergedByteBuffers mbb = new MergedByteBuffers(); //Only used to make it a string.
        mbb.add(bb);
        System.out.println(mbb.getAsString(mbb.remaining()));
        slf.setResult(true);
      }});
    wsc.connect().addCallback(new FutureCallback<Boolean>() {

      @Override
      public void handleResult(Boolean result) {
        //we have to be fully connected before we can write
        wsc.write(ByteBuffer.wrap("TESTDATA".getBytes()), WebSocketOpCodes.Text.getValue(), true);    
      }

      @Override
      public void handleFailure(Throwable t) {
        slf.setFailure(t);
      }});
    
    slf.get(10, TimeUnit.SECONDS);
```
