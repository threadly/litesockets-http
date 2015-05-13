# JavaHttpClient
JavaHttpClient

#HTTPClient Example
Here are some simple examples of using the HTTPClient call.

##Simple Blocking Request
```java
    HTTPClient httpClient = new HTTPClient();
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHost("google.com").setPort(80).setReadTimeout(5000);
    HTTPResponse response = httpClient.request(hrb.build());
    if(! response.hasError()) {
      System.out.println(response.getHeaders());
      System.out.println(response.getBodyAsString());
    } else {
      response.getError().printStackTrace();
    }
```


##Simple Async Request
```java
    HTTPClient httpClient = new HTTPClient();
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHost("google.com").setPort(80).setReadTimeout(5000);
    ListenableFuture<HTTPResponse> lfr1 = httpClient.requestAsync(hrb.build());
    ListenableFuture<HTTPResponse> lfr2 = httpClient.requestAsync(hrb.build());
    ListenableFuture<HTTPResponse> lfr3 = httpClient.requestAsync(hrb.build());
    FutureCallback<HTTPResponse> fc = new FutureCallback<HTTPResponse>() {
      @Override
      public void handleResult(HTTPResponse result) {
        if(! result.hasError()) {
          System.out.println(result.getHeaders());
          System.out.println(result.getBodyAsString());
        } else {
          result.getError().printStackTrace();
        }
      }
      @Override
      public void handleFailure(Throwable t) {
        System.out.println("ERROR:");
        t.printStackTrace();
      }};
      
      lfr1.addCallback(fc);
      lfr2.addCallback(fc);
      lfr3.addCallback(fc);
    
    while(!lfr1.isDone() && !lfr2.isDone() && !lfr3.isDone()) {
      Thread.sleep(100);
    }
```

#HTTPStreamClient Examples
Here are some examples using the HTTPStreamClient

##Simple Incoming Data Request
```java
    ThreadedSocketExecuter TSE = new ThreadedSocketExecuter();
    TSE.start();
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHost("www.google.com").setPort(80);
    HTTPStreamClient client = new HTTPStreamClient("www.google.com", 80, 10000);
    client.setReader(new Reader() {
      MergedByteBuffers mbb = new MergedByteBuffers();
      @Override
      public void onRead(Client client) {
        mbb.add(client.getRead());
        System.out.println(mbb.getAsString(mbb.remaining()));
      }});
    TSE.addClient(client);
    System.out.println(hrb.buildHeadersOnly().getRequestHeaders());
    ListenableFuture<HTTPResponse> lfr = client.writeRequest(hrb.buildHeadersOnly());
    lfr.addCallback(new FutureCallback<HTTPResponse>() {
      @Override
      public void handleResult(HTTPResponse result) {
        if(! result.hasError()) {
          System.out.println(result.getHeaders());
        } else {
          result.getError().printStackTrace();
        }
      }
      @Override
      public void handleFailure(Throwable t) {
        System.out.println("ERROR:");
        t.printStackTrace();
      }});
    //This is non-blocking we are only sleeping to not exit before we can run
    Thread.sleep(3000);
```

##Chunked Outgoing Data
```java
    ThreadedSocketExecuter TSE = new ThreadedSocketExecuter();
    TSE.start();
    HTTPRequestBuilder hrb = new HTTPRequestBuilder().setHost("hosttopost.none").setPort(80).setChunked().setRequestType(HTTPConstants.REQUEST_TYPE.POST);
    HTTPStreamClient client = new HTTPStreamClient("hosttopost.none", 80, 10000);
    client.setReader(new Reader() {
      MergedByteBuffers mbb = new MergedByteBuffers();
      @Override
      public void onRead(Client client) {
        mbb.add(client.getRead());
        System.out.println(mbb.getAsString(mbb.remaining()));
      }});
    TSE.addClient(client);
    System.out.println(hrb.buildHeadersOnly().getRequestHeaders());
    ListenableFuture<HTTPResponse> lfr = client.writeRequest(hrb.buildHeadersOnly());
    lfr.addCallback(new FutureCallback<HTTPResponse>() {
      @Override
      public void handleResult(HTTPResponse result) {
        if(! result.hasError()) {
          System.out.println(result.getHeaders());
        } else {
          result.getError().printStackTrace();
        }
      }
      @Override
      public void handleFailure(Throwable t) {
        System.out.println("ERROR:");
        t.printStackTrace();
      }});
    
    client.writeBlocking(ByteBuffer.wrap("EACH".getBytes()));
    client.writeBlocking(ByteBuffer.wrap("WRITE".getBytes()));
    client.writeBlocking(ByteBuffer.wrap("IS".getBytes()));
    client.writeBlocking(ByteBuffer.wrap("A".getBytes()));
    client.writeBlocking(ByteBuffer.wrap("CHUNK".getBytes()));
    //Ends the chunks.
    client.writeBlocking(ByteBuffer.wrap(new byte[0]));
    //make sure they responded.
    lfr.get();
    lfr = client.writeRequest(hrb.buildHeadersOnly());
    client.writeBlocking(ByteBuffer.wrap("NOW WE POST AGAIN".getBytes()));
    client.writeBlocking(ByteBuffer.wrap(new byte[0]));
    lfr.get();
```