package org.threadly.litesockets.protocols.http.shared;

/**
 * Enum of all standard HTTP Response codes.
 * 
 * @author lwahlmeier
 *
 */
public enum HTTPResponseCode {
  //100 codes
  Continue(100, "Continue"), SwitchingProtocols(101, "Switching Protocols"), Processing(102, "Processing"),
  //200 codes
  OK(200, "OK"), Created(201, "Created"), Accepted(202, "Accepted"), 
  NonAuthoritativeInformation(203, "Non-Authoritative Information"), NoContent(204, "No Content"), 
  ResetContent(205, "Reset Content"), PartialContent(206, "Partial Content"),
  MultiStatus(207, "Multi-Status"), AlreadyReported(208, "Already Reported"), IMUsed(226, "IM Used"),
  //300 Codes
  MultipleChoices(300, "Multiple Choice"), MovedPermanently(301, "Moved Permanently"), 
  Found(302, "Found"), SeeOther(303, "See Other"), NotModified(304, "Not Modified"), 
  Unused(305, "(Unused)"), SwitchProxy(306, "Switch Proxy"),
  TemporaryRedirect(307, "Temporary Redirect"), PermanentRedirect(308, "Permanent Redirect"),
  //400 Codes
  BadRequest(400, "Bad Request"), Unauthorized(401, "Unauthorized"), PaymentRequired(402, "Payment Required"), 
  Forbidden(403, "Forbidden"), NotFound(404, "Not Found"), MethodNotAllowed(405, "Method Not Allowed"), 
  NotAcceptable(406, "Not Acceptable"), ProxyAuthenticationRequired(407, "Proxy Authentication Required"), 
  RequestTimeout(408, "Request Timeout"), Conflict(409, "Conflict"), Gone(410, "Gone"), 
  LengthRequired(411, "Length Required"), PreconditionFailed(412, "Precondition Failed"), 
  RequestEntityTooLarge(413, "Request Entity Too Large"), RequestURITooLong(414, "Request-URI Too Long"), 
  UnsupportedMediaType(415, "Unsupported Media Type"), RequestedRangeNotSatisfiable(416, "Requested Range Not Satisfiable"), 
  ExpectationFailed(417, "Expectation Failed"), ImAteapot(418, "I'm a teapot"), MisdirectedRequest(421, "Misdirected Request"),
  UnprocessableEntity(422, "Unprocessable Entity"), Locked(423, "Locked"), FailedDependency(424, "Failed Dependency"),
  UpgradeRequired(426, "Upgrade Required"), PreconditionRequired(428, "Precondition Required"), TooManyRequests(429, "Too Many Requests"),
  RequestHeaderFieldsTooLarge(431, "Request Header Fields Too Large"), UnavailableForLegalReasons(451,"Unavailable For Legal Reasons"),
  //500 Codes
  InternalServerError(500, "Internal Server Error"), NotImplemented(501, "Not Implemented"), BadGateway(502, "Bad Gateway"), 
  ServiceUnavailable(503, "Service Unavailable"), GatewayTimeout(504, "Gateway Timeout"), 
  HTTPVersionNotSupported(505, "HTTP Version Not Supported"), VariantAlsoNegotiates(506, "Variant Also Negotiates"),
  InsufficientStorage(507, "Insufficient Storage"), LoopDetected(508, "Loop Detected"), NotExtended(510, "Not Extended"),
  NetworkAuthenticationRequired(511, "Network Authentication Required");
  
  private int val;
  private String text;
  private HTTPResponseCode(int val, String text) {
    this.val = val;
    this.text = text;
  }
  
  public int getId() {
    return val;
  }
  
  @Override
  public String toString() {
    return text;
  }
  
  public static HTTPResponseCode findResponseCode(int val) {
    for(HTTPResponseCode hrc: HTTPResponseCode.values()) { 
      if(hrc.getId() == val) {
        return hrc;
      }
    }
    throw new IllegalArgumentException("Could not find ResponseCode: " + val);
  }
}
