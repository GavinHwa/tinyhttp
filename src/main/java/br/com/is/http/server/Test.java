package br.com.is.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Test {
  public static void main(String... args) throws InterruptedException, IOException {
    LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME).setLevel(Level.FINE); 
 
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10, HTTPServer.Type.HTTP);

    server.addContext("/test.html", new HTTPContext() {

      @Override
      public void doGet(HTTPRequest req, HTTPResponse resp) {
        HTTPSession session = req.getSession();
        OutputStream ou = resp.getOutputStream();

        session.setAttribute("Teste", "Test");
        try {
          ou.write("abc abc abc abc abc abc abc def jhiabc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi abc abc abc abc def jhi".getBytes());
          ou.write("TEST".getBytes());
          ou.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });

    server.run();
    while (true) {
      Thread.sleep(1000);
    }
  }
}
