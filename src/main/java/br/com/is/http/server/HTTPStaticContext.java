/* Copyright (C) 2013 Leonardo Bispo de Oliveira
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package br.com.is.http.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.util.JAXBSource;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import br.com.is.http.server.exception.BadRequestException;
import br.com.is.http.server.exception.HTTPRequestException;
import br.com.is.http.server.exception.InternalServerErrorException;
import br.com.is.http.server.exception.RequestRangeNotSatisfiableException;

//TODO: Implement the Authentication Method.!!
final class HTTPStaticContext extends HTTPContext {
  private static final int DEFAULT_BUFFER_SIZE    = 1024 * 16;
  private static final String XSLT                = "META-INF/directory.xsl";  
  private static final String MIME_DEFAULT_BINARY = "application/octet-stream";
  
  private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  
  private final String path;
  private final List<String> defaultPages;

  private static Hashtable<String, String> MimeTypes = new Hashtable<>();
  static {
    MimeTypes.put("css"  , "text/css"                     );
    MimeTypes.put("htm"  , "text/html"                    );
    MimeTypes.put("html" , "text/html"                    );
    MimeTypes.put("xml"  , "text/xml"                     );
    MimeTypes.put("txt"  , "text/plain"                   );
    MimeTypes.put("asc"  , "text/plain"                   );
    MimeTypes.put("gif"  , "image/gif"                    );
    MimeTypes.put("jpg"  , "image/jpeg"                   );
    MimeTypes.put("jpeg" , "image/jpeg"                   );
    MimeTypes.put("png"  , "image/png"                    );
    MimeTypes.put("mp3"  , "audio/mpeg"                   );
    MimeTypes.put("m3u"  , "audio/mpeg-url"               );
    MimeTypes.put("mp4"  , "video/mp4"                    );
    MimeTypes.put("ogv"  , "video/ogg"                    );
    MimeTypes.put("flv"  , "video/x-flv"                  );
    MimeTypes.put("mov"  , "video/quicktime"              );
    MimeTypes.put("swf"  , "application/x-shockwave-flash");
    MimeTypes.put("js"   , "application/javascript"       );
    MimeTypes.put("pdf"  , "application/pdf"              );
    MimeTypes.put("doc"  , "application/msword"           );
    MimeTypes.put("ogg"  , "application/x-ogg"            );
    MimeTypes.put("zip"  , MIME_DEFAULT_BINARY            );
    MimeTypes.put("exe"  , MIME_DEFAULT_BINARY            );
    MimeTypes.put("class", MIME_DEFAULT_BINARY            );
  }
  
  @SuppressWarnings("unused")
  private static class FileInfo {
    public String  file;
    public boolean isDir;
    public String  uri;
    public long    length;
  }
  
  @XmlRootElement(name = "files")
  private static class Files
  {
    @XmlElement(name = "fileinfo")
    public final List<FileInfo> files = new ArrayList<>();
  }
  
  public HTTPStaticContext(final String path) {
    this.path         = path;
    this.defaultPages = new ArrayList<>();
  }
  
  public HTTPStaticContext(final String path, final List<String> defaultPages) {
    this.path         = path;
    this.defaultPages = defaultPages;
  }
  
  @Override
  public void doGet(final HTTPRequest req, final HTTPResponse resp) { 
    try {
      final String uri = normalizeURI(req.getRequestURI());

      final File staticUriInfo = new File(path, uri);
      File staticFile = null;
      
      resp.addHeader("Accept-Ranges", "bytes");
      if (staticUriInfo.exists()) {
        if (staticUriInfo.isDirectory()) {
          if (uri.endsWith("/")) {
            for (String page : defaultPages) {
              File tmp = new File(staticUriInfo, page);
              if (tmp.exists()) {
                staticFile = tmp;
                break;
              }
            }
            
            if (staticFile == null)
              processDirectory(req, resp, staticUriInfo, uri);
          }
          else
            resp.sendRedirect(uri + "/");
        }
        else
          staticFile = staticUriInfo;
        
        if (staticFile != null)
          processFile(req, resp, staticFile);
      }
      else
        resp.setStatus(HTTPStatus.NOT_FOUND);
    }
    catch (HTTPRequestException e) {
      if (LOGGER.isLoggable(Level.WARNING))
        LOGGER.log(Level.WARNING, "Problems Create an HTTP Response", e);

      resp.setStatus(e.getError());
    }
  }
  
  private void processDirectory(final HTTPRequest req, final HTTPResponse resp, final File dir, final String uri) throws HTTPRequestException {
    if (!dir.canRead())
      throw new BadRequestException("Directory not accessible");
    
    final Files files = new Files();
    
    if (uri.length() > 1) {
      final String u = uri.substring(0, uri.length() - 1);
      final int slash = u.lastIndexOf('/');
      if (slash >= 0 && slash  < u.length()) {
        final FileInfo info = new FileInfo();
        info.file   = "..";
        info.isDir  = true;
        info.uri    = encodeUri(uri.substring(0, slash + 1));
        files.files.add(info);
      }
    }
    
    for (String file : dir.list()) {
      final File curr     = new File(dir, file);
      final FileInfo info = new FileInfo();

      info.file   = file;
      info.isDir  = curr.isDirectory();
      info.uri    = encodeUri(uri + file);
      info.length = curr.length();
      files.files.add(info);
    }
    
    try {
      final TransformerFactory tf   = TransformerFactory.newInstance();
      final Transformer transformer = tf.newTransformer(new StreamSource(this.getClass().getClassLoader().getResourceAsStream(XSLT)));
      final JAXBSource source       = new JAXBSource(JAXBContext.newInstance(Files.class), files);
      final StreamResult result     = new StreamResult(resp.getOutputStream());

      transformer.transform(source, result);
    }
    catch (TransformerException | JAXBException e) {
      throw new InternalServerErrorException("Problems to Generate the directory template", e);
    }
  }
  
  private void processFile(final HTTPRequest req, final HTTPResponse resp, final File file) throws HTTPRequestException {
    if (!file.canRead())
      throw new BadRequestException("File not accessible");

    String mime = null;
    try {
      final int dot = file.getCanonicalPath().lastIndexOf('.');
      if (dot >= 0)
        mime = MimeTypes.get(file.getCanonicalPath().substring(dot + 1).toLowerCase());
    }
    catch (IOException e) {
      throw new InternalServerErrorException("Problems to get information from the file", e);
    }
    if (mime == null)
      mime = MIME_DEFAULT_BINARY;

    final String etag = Integer.toHexString((file.getAbsolutePath() + file.lastModified() + "" + file.length()).hashCode());

    long startFrom = 0;
    long endAt     = -1;
    String range = req.getHeader("range");
    if (range != null) {
      if (range.startsWith("bytes=")) {
        range = range.substring("bytes=".length());
        final int minus = range.indexOf('-');
        try {
          if (minus > 0) {
            startFrom = Long.parseLong(range.substring(0, minus));
            endAt     = Long.parseLong(range.substring(minus + 1));
          }
        }
        catch (NumberFormatException e) {}
      }
    }
    
    final long fileLen = file.length();
    if (range != null && startFrom >= 0) {
      if (endAt < 0)
        endAt = fileLen - 1;

      final long lenToRead = endAt - startFrom + 1;

      if (startFrom >= fileLen || lenToRead <= 0) {
        resp.addHeader("Content-Range", "bytes 0-0/" + fileLen);
        resp.addHeader("ETag", etag);

        if (mime.startsWith( "application/"))
          resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

        throw new RequestRangeNotSatisfiableException("Not valid range passed in the HTTP header");
      }
      else {
        try {
          final InputStream is = new FileInputStream(file);
          
          is.skip(startFrom);
          
          copy(is, resp.getOutputStream(), lenToRead);
          is.close();
          resp.addHeader("Content-Length", Long.toString(lenToRead));
          resp.addHeader( "Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
          if (mime.startsWith("application/"))
            resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

          resp.addHeader("ETag", etag);
          
          resp.setStatus(HTTPStatus.PARTIAL_CONTENT);
        }
        catch (IOException e) {
          throw new InternalServerErrorException("Problems to open the file", e);
        }
      }
    }
    else if (!etag.equals(req.getHeader("if-none-match"))) {
      resp.addHeader("Content-Length", Long.toString(fileLen));
      if (mime.startsWith("application/" ))
        resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

      try {
        FileInputStream fis = new FileInputStream(file);
        copy(fis, resp.getOutputStream(), fileLen);
      }
      catch (IOException e) {
        throw new InternalServerErrorException("Problems to open the file", e);
      }

      resp.addHeader("ETag", etag);
    }
    else
      resp.setStatus(HTTPStatus.NOT_MODIFIED);
  }
  
  private String normalizeURI(String uri) throws HTTPRequestException {
    uri = uri.trim().replace(File.separatorChar, '/');
    if (uri.indexOf('?') >= 0 )
      uri = uri.substring(0, uri.indexOf('?'));

    if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0)
      throw new BadRequestException("Cannot call .. to navigate between folders");
    
    return uri;
  }
  
  private String encodeUri(String uri) throws HTTPRequestException {
    String newUri = "";
    StringTokenizer st = new StringTokenizer(uri, "/ ", true);
    while (st.hasMoreTokens()) {
      String tok = st.nextToken();
      if (tok.equals("/"))
        newUri += "/";
      else if (tok.equals(" "))
        newUri += "%20";
      else {
        try {
          newUri += URLEncoder.encode(tok, "ASCII");
        }
        catch (UnsupportedEncodingException e) {
          throw new InternalServerErrorException("Problems to encode the new URI.", e);
        }
      }
    }
    return newUri;
  }
  
  public static int copy(InputStream input, OutputStream output, long lenToRead) throws IOException {
    long count = copyLarge(input, output, lenToRead);
    if (count > Integer.MAX_VALUE) {
      return -1;
    }
    return (int) count;
  }
  
  public static long copyLarge(InputStream input, OutputStream output, long lenToRead) throws IOException {
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    long count = 0;
    int n = 0;
    int flush = 0;
    while (-1 != (n = input.read(buffer)) && lenToRead > 0) {
      final int copied = n > (int) lenToRead ? (int) lenToRead : n;
      output.write(buffer, 0, copied);
      count += copied;
      flush += copied;
      if (flush > (DEFAULT_BUFFER_SIZE * 30000)) {
        output.flush();
        flush = 0;
      }
      lenToRead -= copied;
    }
    
    return count;
  }
}
