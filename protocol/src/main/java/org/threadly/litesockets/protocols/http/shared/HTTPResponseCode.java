package org.threadly.litesockets.protocols.http.shared;

/**
 * Enum of all known HTTP Response codes.
 */
public enum HTTPResponseCode {
  //100 codes
  Continue(100, "Continue"), SwitchingProtocols(101, "Switching Protocols"), 
  Processing(102, "Processing") /*(WebDAV; RFC 2518)*/,
  //200 codes
  OK(200, "OK"), Created(201, "Created"), Accepted(202, "Accepted"), 
  NonAuthoritativeInformation(203, "Non-Authoritative Information"), NoContent(204, "No Content"), 
  ResetContent(205, "Reset Content"), PartialContent(206, "Partial Content"),
  MultiStatus(207, "Multi-Status") /*(WebDAV; RFC 4918)*/, 
  AlreadyReported(208, "Already Reported") /*(WebDAV; RFC 5842)*/, 
  LowOnStorageSpace(250, "Low On Storage Space") /*(RTSP; RFC 2326)*/, 
  IMUsed(226, "IM Used") /*(RFC 3229)*/,
  //300 Codes
  MultipleChoices(300, "Multiple Choice"), MovedPermanently(301, "Moved Permanently"), 
  Found(302, "Found"), SeeOther(303, "See Other"), NotModified(304, "Not Modified"), 
  Unused(305, "(Unused)"), SwitchProxy(306, "Switch Proxy"),
  TemporaryRedirect(307, "Temporary Redirect"), PermanentRedirect(308, "Permanent Redirect"),
  //400 Codes
  BadRequest(400, "Bad Request"), Unauthorized(401, "Unauthorized"), 
  PaymentRequired(402, "Payment Required"), Forbidden(403, "Forbidden"), NotFound(404, "Not Found"), 
  MethodNotAllowed(405, "Method Not Allowed"), NotAcceptable(406, "Not Acceptable"), 
  ProxyAuthenticationRequired(407, "Proxy Authentication Required"), 
  RequestTimeout(408, "Request Timeout"), Conflict(409, "Conflict"), Gone(410, "Gone"), 
  LengthRequired(411, "Length Required"), PreconditionFailed(412, "Precondition Failed"), 
  RequestEntityTooLarge(413, "Request Entity Too Large"), RequestURITooLong(414, "Request-URI Too Long"), 
  UnsupportedMediaType(415, "Unsupported Media Type"), 
  RequestedRangeNotSatisfiable(416, "Requested Range Not Satisfiable"), 
  ExpectationFailed(417, "Expectation Failed"), ImAteapot(418, "I'm a teapot") /*(RFC 2324)*/, 
  EnhanceYourClaim(420, "Enhance Your Claim") /*(Twitter)*/, 
  MisdirectedRequest(421, "Misdirected Request"),
  UnprocessableEntity(422, "Unprocessable Entity") /*(WebDAV; RFC 4918)*/, 
  Locked(423, "Locked") /*(WebDAV; RFC 4918)*/, 
  FailedDependency(424, "Failed Dependency") /*(WebDAV; RFC 4918)*/, 
  UnorderedCollection(425, "Unordered Collection") /*(Internet draft)*/,
  UpgradeRequired(426, "Upgrade Required") /*(RFC 2817)*/, 
  PreconditionRequired(428, "Precondition Required") /*(RFC 6585)*/, 
  TooManyRequests(429, "Too Many Requests") /*(RFC 6585)*/,
  RequestHeaderFieldsTooLarge(431, "Request Header Fields Too Large") /*(RFC 6585)*/, 
  BlockedByWindowsParentalControls(450, "Blocked by Windows Parental Controls") /*(Microsoft)*/, 
  UnavailableForLegalReasons(451,"Unavailable For Legal Reasons") /*(Internet draft)*/,
  ConferenceNotFound(452, "Conference Not Found") /*(RTSP)*/, 
  NotEnoughhBandwidth(453, "Not Enough Bandwidth") /*(RTSP)*/, 
  SessionNotFound(454, "Session Not Found") /*(RTSP)*/, 
  MethodNotValidInThisState(455, "Method Not Valid in This State") /*(RTSP)*/, 
  HeaderFieldNotValidForRequest(456, "Header Field Not Valid for Resource") /*(RTSP)*/, 
  InvalidRange(457, "Invalid Range") /*(RTSP)*/, 
  ParameterIsReadOnly(458, "Parameter Is Read-Only") /*(RTSP)*/, 
  AggregateOperationNotAllowed(459, "Aggregate Operation Not Allowed") /*(RTSP)*/, 
  OnlyAggregateOperationAllowed(460, "Only Aggregate Operation Allowed") /*(RTSP)*/, 
  UnsupportedTransport(461, "Unsupported Transport") /*(RTSP)*/, 
  DestinationUnreachable(462, "Destination Unreachable") /*(RTSP)*/, 
  RequestHeaderTooLarge(494, "Request Header Too Large") /*(Nginx)*/, 
  CertError(495, "Cert Error") /*(Nginx)*/, NoCert(496, "No Cert") /*(Nginx)*/, 
  HttpToHttps(497, "HTTP to HTTPS") /*(Nginx)*/, 
  ClientClosedRequest(499, "Client Closed Request") /*(Nginx)*/, 
  //500 Codes
  InternalServerError(500, "Internal Server Error"), NotImplemented(501, "Not Implemented"), 
  BadGateway(502, "Bad Gateway"), ServiceUnavailable(503, "Service Unavailable"), 
  GatewayTimeout(504, "Gateway Timeout"), HTTPVersionNotSupported(505, "HTTP Version Not Supported"), 
  VariantAlsoNegotiates(506, "Variant Also Negotiates") /*(RFC 2295)*/, 
  InsufficientStorage(507, "Insufficient Storage") /*(WebDAV; RFC 4918)*/, 
  LoopDetected(508, "Loop Detected") /*(WebDAV; RFC 5842)*/, 
  NotExtended(510, "Not Extended") /*(RFC 2774)*/, 
  NetworkAuthenticationRequired(511, "Network Authentication Required") /*(RFC 6585)*/, 
  OptionNotSupported(551, "Option not supported") /*(RTSP)*/;
  
  private int val;
  private String text;
  private HTTPResponseCode(int val, String text) {
    this.val = val;
    this.text = text;
  }
  
  /**
   * Return the http standard numeric code / id associated to the response code.
   * @return The id value
   */
  public int getId() {
    return val;
  }
  
  @Override
  public String toString() {
    return text;
  }
  
  /**
   * Attempt to convert a response code from a numeric id to an enum value.
   * 
   * @param val The value to check for match
   * @return A matching response code enum
   * @throws IllegalArgumentException thrown if no code is associated with the provided value
   */
  public static HTTPResponseCode findResponseCode(int val) {
    for(HTTPResponseCode hrc: HTTPResponseCode.values()) { 
      if(hrc.getId() == val) {
        return hrc;
      }
    }
    throw new IllegalArgumentException("Could not find ResponseCode: " + val);
  }
}
