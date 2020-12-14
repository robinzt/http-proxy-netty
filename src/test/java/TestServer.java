import com.skywing.proxy.http.HttpProxyServer;
import org.junit.Test;

public class TestServer {

    @Test
    public void testHttpProxyServer() {
        HttpProxyServer proxyServer = new HttpProxyServer("0.0.0.0", 8080);
        proxyServer.start();
    }
}
