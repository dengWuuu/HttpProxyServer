package handler.proxy;


import bean.ClientRequest;
import bean.Const;
import handler.edit.Editor;
import handler.response.HttpProxyResponseHandler;
import handler.utils.ProxyRequestUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.Attribute;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bean.Const.CLIENT_REQUEST_ATTRIBUTE_KEY;

/**
 * 对HTTP请求代理
 */
public class HttpProxyHandler extends ChannelInboundHandlerAdapter implements IProxyHandler {
    private final Logger logger = LoggerFactory.getLogger(HttpProxyHandler.class);

    // 获取从客户端发过来的消息
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.debug("[HttpProxyHandler]");
        if (msg instanceof HttpRequest httpRequest) {
            //获取客户端请求
            ClientRequest clientRequest = ProxyRequestUtil.getClientRequest(ctx.channel());
            if (clientRequest == null) {
                //从本次请求中获取
                Attribute<ClientRequest> clientRequestAttribute = ctx.channel().attr(CLIENT_REQUEST_ATTRIBUTE_KEY);
                clientRequest = ProxyRequestUtil.getClientRequest(httpRequest);
                //将clientRequest保存到channel中
                clientRequestAttribute.setIfAbsent(clientRequest);
            }
            //如果是connect代理请求，返回成功以代表代理成功
            if (sendSuccessResponseIfConnectMethod(ctx, httpRequest.method().name())) {
                logger.debug("[HttpProxyHandler][channelRead] sendSuccessResponseConnect");
                ctx.channel().pipeline().remove("httpRequestDecoder");
                ctx.channel().pipeline().remove("httpResponseEncoder");
                ctx.channel().pipeline().remove("httpAggregator");
                ReferenceCountUtil.release(msg);
                return;
            }
            if (clientRequest.isHttps()) {
                super.channelRead(ctx, msg);
                return;
            }
            // 篡改请求报文
            Object new_msg = Editor.editRequest(clientRequest, msg);
            // 将篡改后的请求发送给服务器
            sendToServer(clientRequest, ctx, new_msg);
            return;
        }
        super.channelRead(ctx, msg);
    }

    /**
     * 如果是connect请求的话，返回连接建立成功
     *
     * @param ctx        ChannelHandlerContext
     * @param methodName 请求类型名
     * @return 是否为connect请求
     */
    private boolean sendSuccessResponseIfConnectMethod(ChannelHandlerContext ctx, String methodName) {
        if (Const.CONNECT_METHOD_NAME.equalsIgnoreCase(methodName)) {
            //代理建立成功
            //HTTP代理建立连接
            HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, Const.CONNECT_SUCCESS);
            ctx.writeAndFlush(response);
            return true;
        }
        return false;
    }


    @Override
    public void sendToServer(ClientRequest clientRequest, ChannelHandlerContext ctx, Object msg) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                // 注册线程池
                .channel(ctx.channel().getClass())
                // 使用NioSocketChannel来作为连接用的channel类
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        //添加接收远程server的handler
                        ch.pipeline().addLast(new HttpRequestEncoder());
                        ch.pipeline().addLast(new HttpResponseDecoder());
                        ch.pipeline().addLast(new HttpObjectAggregator(6553600));
                        //代理handler,负责给客户端响应结果
                        ch.pipeline().addLast(new HttpProxyResponseHandler(ctx.channel()));
                    }
                });

        //连接远程server
        ChannelFuture cf = bootstrap.connect(clientRequest.getHost(), clientRequest.getPort());
        cf.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                //连接成功
                future.channel().writeAndFlush(msg);
                logger.debug("[operationComplete] connect remote server success!");
            } else {
                //连接失败
                logger.error("[operationComplete] 连接远程server失败了");
                ctx.channel().close();
            }
        });
    }

    @Override
    public void sendToClient(ClientRequest clientRequest, ChannelHandlerContext ctx, Object msg) {
        try {
            logger.debug("发送http包给客户端");
            ctx.pipeline().addFirst("httpRequestDecoder", new HttpRequestDecoder());
            //发送响应给客户端，并将发送内容编码
            ctx.pipeline().addFirst("httpResponseEncoder", new HttpResponseEncoder());
            //http聚合
            ctx.pipeline().addLast("httpAggregator", new HttpObjectAggregator(65536));
            // 重新过一遍pipeline，拿到解密后的的http报文
            ctx.pipeline().fireChannelRead(msg);
            Attribute<ClientRequest> clientRequestAttribute = ctx.channel().attr(CLIENT_REQUEST_ATTRIBUTE_KEY);
            clientRequest.setHttps(true);
            clientRequestAttribute.set(clientRequest);
        } catch (Exception e) {
            logger.error("[sendToServer] err:{}", e.getMessage());
        }
    }
}
