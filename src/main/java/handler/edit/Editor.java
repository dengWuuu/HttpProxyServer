package handler.edit;


import bean.ClientRequest;
import io.netty.handler.codec.http.HttpRequest;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 修改http和https报文
 */
public class Editor {
    public static Object editRequest(ClientRequest clientRequest, Object msg) {
        HttpRequest new_msg = (HttpRequest) msg;
        String host = clientRequest.getHost();
        if ("110.65.10.252".equals(host)) {
            new_msg.setUri("/cxxl/ShowNews.aspx?NewsNo=E03104625029843B");
        }
        return new_msg;
    }

    private static void writeLog(String str) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter("test.log"));
        bw.append(str);
        bw.newLine();
        bw.flush();
    }
}
