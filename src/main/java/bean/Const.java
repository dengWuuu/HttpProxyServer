package bean;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AttributeKey;

/**
 * 常量表
 */

public class Const {
    /**
     * connect类型请求名称
     */
    public static final String CONNECT_METHOD_NAME = "CONNECT";
    /**
     * https连接协议名
     */
    public static final String HTTPS_PROTOCOL_NAME = "https";
    /**
     * 代理握手成功响应status
     */
    public final static HttpResponseStatus CONNECT_SUCCESS = new HttpResponseStatus(200, "Connection established");
    /**
     * channel中的clientRequest
     */
    public static final AttributeKey<ClientRequest> CLIENT_REQUEST_ATTRIBUTE_KEY = AttributeKey.valueOf("clientRequest");
}
