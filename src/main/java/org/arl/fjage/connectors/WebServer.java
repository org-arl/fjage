/******************************************************************************

 Copyright (c) 2013, Mandar Chitre

 This file is part of fjage which is released under Simplified BSD License.
 See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
 for full license details.

 ******************************************************************************/

package org.arl.fjage.connectors;

import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.Rule;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Web server instance manager.
 */
public class WebServer {

  //////// constants

  public static final String NOCACHE = "no-cache, no-store, must-revalidate";
  public static final String CACHE = "public, max-age=31536000";

  //////// static attributes and methods

  private static final Map<Integer,WebServer> servers = new HashMap<>();
  private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(WebServer.class.getName());

  static {
    // disable Jetty logging (except warnings)
    System.setProperty("org.eclipse.jetty.LEVEL", "WARN");
    Log.setLog(new Logger() {
      @Override public String getName()                         { return "[jetty]"; }
      @Override public Logger getLogger(String name)            { return this;      }
      @Override public boolean isDebugEnabled()                 { return false;     }
      @Override public void warn(String msg, Object... args)    {
        if (msg.contains("{}")) for (Object a: args) msg = msg.replaceFirst("\\{}", a.toString());
        log.warning(msg);
      }
      @Override public void warn(Throwable t)                   { log.log(Level.WARNING, "", t); }
      @Override public void warn(String msg, Throwable thrown)  { log.log(Level.WARNING, msg, thrown); }
      @Override public void info(String msg, Object... args)    { }
      @Override public void info(Throwable thrown)              { }
      @Override public void info(String msg, Throwable thrown)  { }
      @Override public void setDebugEnabled(boolean enabled)    { }
      @Override public void debug(String msg, Object... args)   { }
      @Override public void debug(String msg, long x)           { }
      @Override public void debug(Throwable thrown)             { }
      @Override public void debug(String msg, Throwable thrown) { }
      @Override public void ignore(Throwable ignored)           { }
    });
  }

  /**
   * Gets an instance of a web server running on the specified port. If an instance is not
   * already available, a new one is created.
   *
   * @param port HTTP port number.
   */
  public static WebServer getInstance(int port) {
    synchronized (servers) {
      WebServer svr = servers.get(port);
      if (svr == null || svr.server == null) svr = new WebServer(port);
      return svr;
    }
  }

  /**
   * Gets an instance of a web server running on the specified port. If an instance is not
   * already available, a new one is created.
   *
   * @param port HTTP port number.
   * @param ip IP address to bind HTTP server to.
   */
  public static WebServer getInstance(int port, String ip) {
    synchronized (servers) {
      WebServer svr = servers.get(port);
      if (svr == null || svr.server == null) svr = new WebServer(port, ip);
      return svr;
    }
  }

  /**
   * Checks if an instance of a web server is running on the specified port.
   *
   * @param port HTTP port number.
   * @return true if running, false otherwise.
   */
  public static boolean hasInstance(int port) {
    synchronized (servers) {
      WebServer svr = servers.get(port);
      return svr != null;
    }
  }

  /**
   * Gets all web server instances running.
   *
   * @return array of web server instances.
   */
  public static WebServer[] getInstances() {
    synchronized (servers) {
      return servers.values().toArray(new WebServer[0]);
    }
  }

  /**
   * Shutdown all web servers.
   */
  public static void shutdown() {
    synchronized (servers) {
      for (WebServer svr: servers.values())
        svr.stop();
    }
  }

  //////// instance attributes and methods

  protected Server server;
  protected ContextHandlerCollection contexts;
  protected RewriteHandler rewrite;
  protected boolean started;
  protected int port;

  protected WebServer(int port) {
    this(port, "127.0.0.1");
  }


  /**
   * Creates a new web server instance.
   * <br>
   * The Jetty based WebServer has a set of handlers that are initialized
   * and more handlers can be added to it. The handler stack setup is
   * <p>
   * <code> GzipHandler -> RewriteHandler -> ContextHandlerCollection[ contexts[] , DefaultHandler] </code>
   * </p>
   * Any new handlers added to the server will be added to the list of ContextHandlers (contexts).
   *
   * @param port HTTP port number.
   * @param ip IP address to bind HTTP server to.
   */
  protected WebServer(int port, String ip) {
    this.port = port;
    server = new Server(InetSocketAddress.createUnresolved(ip, port));
    server.setStopAtShutdown(true);
    if (port > 0) servers.put(port, this);
    rewrite = new RewriteHandler();
    rewrite.setRewriteRequestURI(true);
    rewrite.setRewritePathInfo(true);
    contexts = new ContextHandlerCollection();
    GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setIncludedMimeTypes("text/html", "text/plain", "text/xml", "text/css", "application/javascript", "text/javascript");
    HandlerCollection handlerCollection = new HandlerCollection();
    handlerCollection.setHandlers(new Handler[] { contexts, new DefaultHandler() });
    gzipHandler.setHandler(rewrite);
    rewrite.setHandler(handlerCollection);
    server.setHandler(gzipHandler);
    ThreadPool pool = server.getThreadPool();
    if (pool instanceof QueuedThreadPool) ((QueuedThreadPool)pool).setDaemon(true);
    started = false;
  }

  /**
   * Gets the port number that the web server is running on.
   *
   * @return TCP port number.
   */
  public int getPort() {
    return port;
  }

  /**
   * Starts the web server.
   */
  public void start() {
    if (started) return;
    try {
      server.start();
      log.info("Started web server on port "+port);
      started = true;
    } catch (Exception ex) {
      log.log(Level.WARNING, "Unable to start web server on port "+port, ex);
    }
  }

  /**
   * Stops the web server. Once this method is called, the server cannot be restarted.
   */
  public void stop() {
    if (server == null) return;
    try {
      log.info("Stopping web server");
      server.stop();
      started = false;
    } catch (Exception ex) {
      log.log(Level.WARNING, "Unable to stop web server", ex);
    }
    server = null;
    contexts = null;
    if (port > 0) servers.remove(port);
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param resource resource path.
   * @param options WebServerOptions object.
   * @return a List of ContextHandler objects if added.
   */
  public List<ContextHandler> addStatic(String context, String resource, WebServerOptions options) {
    if (context == null || context.isEmpty()) throw new IllegalArgumentException("Context cannot be null or empty");
    if (resource == null || resource.isEmpty()) throw new IllegalArgumentException("Resource cannot be null or empty");
    if(resource.startsWith("/")) resource = resource.substring(1);
    ArrayList<URL> res = new ArrayList<>();
    try {
      res = Collections.list(getClass().getClassLoader().getResources(resource));
    }catch (IOException ex){
      // do nothing
    }
    List<ContextHandler> handlers = new ArrayList<>();
    for (URL r : res){
      String staticWebResDir = r.toExternalForm();
      ContextHandler handler = new ContextHandler(context);
      if (options.directoryListed) log.warning("Directory listing is not supported for resources in jars");
      ResourceHandler resHandler = new ResourceHandler();
      resHandler.setResourceBase(staticWebResDir);
      resHandler.setWelcomeFiles(new String[]{ "index.html" });
      resHandler.setDirectoriesListed(false);
      resHandler.setCacheControl(options.cacheControl);
      resHandler.setEtags(true);
      handler.setHandler(resHandler);
      if (add(handler)) handlers.add(handler);
    }
    return handlers;
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param resource resource path.
   * @param cacheControl cache control header.
   * @return a List of ContextHandler objects if added.
   */
  public List<ContextHandler> addStatic(String context, String resource, String cacheControl) {
    return addStatic(context, resource, new WebServerOptions().cacheControl(cacheControl));
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param resource resource path.
   * @return a List of ContextHandler objects if added.
   */
  public List<ContextHandler> addStatic(String context, String resource) {
    return addStatic (context, resource, new WebServerOptions());
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param dir filesystem path of directory to serve files from.
   * @param options WebServerOptions object.
   * @return a List of ContextHandler objects if added.
   */
  public List<ContextHandler> addStatic(String context, File dir, WebServerOptions options) {
    if (context == null || context.isEmpty()) throw new IllegalArgumentException("Context cannot be null or empty");
    if (dir == null || !dir.exists()) throw new IllegalArgumentException("Directory cannot be null and must exist");
    try {
      ContextHandler handler = new ContextHandler(context);
      ResourceHandler resHandler = options.directoryListed ? new DirectoryHandler() : new ResourceHandler();
      resHandler.setResourceBase(dir.getCanonicalPath());
      resHandler.setWelcomeFiles(new String[]{ "index.html" });
      resHandler.setCacheControl(options.cacheControl);
      resHandler.setEtags(true);
      handler.setHandler(resHandler);
      if (add(handler)) return Collections.singletonList(handler);
    }catch (IOException ex){
      log.log(Level.WARNING, "Unable to add context : " + context, ex);
    }
    return Collections.emptyList();
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param dir filesystem path of directory to serve files from.
   * @param cacheControl cache control header.
   * @return ContextHandler object if added, null otherwise.
   */
  public List<ContextHandler> addStatic(String context, File dir, String cacheControl) {
    return addStatic (context, dir, new WebServerOptions().cacheControl(cacheControl));
  }

  /**
   * Adds a context to serve static documents.
   *
   * @param context context path.
   * @param dir filesystem path of directory to serve files from.
   * @return ContextHandler object if added, null otherwise.
   */
  public List<ContextHandler> addStatic(String context, File dir) {
    return addStatic(context, dir, new WebServerOptions());
  }

  /**
   * Removes a context serving static documents.
   *
   * @param handler context handler to remove.
   * @return true if removed, false otherwise.
   */
  public boolean removeStatic(ContextHandler handler) {
    return removeHandler(handler);
  }

  /**
   * Checks is there's already a context serving static documents.
   *
   * @param context context path.
   * @return true if configured, false otherwise.
   */
  public boolean hasStatic(String context) {
    return hasHandler(context);
  }

  /**
   * Adds a context to upload files to.
   *
   * @param context context path.
   * @param dir filesystem path of directory to upload files to.
   * @return true if added, false otherwise.
   */
  public boolean addUpload(String context, File dir) {
    long maxFileSize = 1024 * 1024 * 1024; // 1 GB
    long maxRequestSize = 1024 * 1024 * 1024; // 1 GB
    int fileSizeThreshold = 100*1024*1024; // 100 MB
    return addUpload(context, dir, maxFileSize, maxRequestSize, fileSizeThreshold);
  }

  /**
   * Adds a context to upload files to.
   *
   * @param context context path.
   * @param dir filesystem path of directory to upload files to.
   * @param maxFileSize maximum size of a file. @see javax.servlet.MultipartConfigElement
   * @param maxRequestSize maximum size of a request. @see javax.servlet.MultipartConfigElement
   * @param fileSizeThreshold size threshold after which files will be written to disk. @see javax.servlet.MultipartConfigElement
   * @return true if added, false otherwise.
   */
  public boolean addUpload(String context, File dir, long maxFileSize, long maxRequestSize, int fileSizeThreshold) {
    String location = dir.getAbsolutePath();
    MultipartConfigElement multipartConfig = new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);
    ContextHandler handler = new ContextHandler(context);
    handler.setAllowNullPathInfo(true);
    handler.setHandler(new UploadHandler(multipartConfig, dir.toPath()));
    return add(handler);
  }

  /**
   * Add a handler to the server at the specified context.
   *
   * @param context context path.
   * @param handler handler to add.
   * @return ContextHandler object if added, null otherwise.
   */
  public ContextHandler addHandler(String context, AbstractHandler handler) {
    if (context == null || context.isEmpty()) throw new IllegalArgumentException("Context cannot be null or empty");
    if (handler == null) throw new IllegalArgumentException("Handler cannot be null");
    ContextHandler c = new ContextHandler(context);
    c.setHandler(handler);
    if (add(c)) return c;
    return null;
  }

  /**
   * Checks if a handler is already registered for the specified context.
   *
   * @param context context path.
   * @return true if a handler is already registered, false otherwise.
   */
  public boolean hasHandler(String context) {
    // find any handler in contexts.getHandlers() that has getContextPath() == context
    return Arrays.stream(contexts.getHandlers())
        .filter(h -> h instanceof ContextHandler)
        .map(h -> (ContextHandler) h)
        .anyMatch(h -> h.getContextPath().equals(context));
  }

  /**
   * Removes a ContextHandler from the server.
   *
   * @param handler handler to remove.
   * @return true if removed, false otherwise.
   */
  public boolean removeHandler(ContextHandler handler) {
    if (handler == null) throw new IllegalArgumentException("Handler cannot be null");
    return remove(handler);
  }

  /**
   * Adds a rule to rewrite handler.
   * <p>
   * NOTE: Rules cannot be added after the server is started.
   * </p>
   * @param rule rewrite rule.
   * @return true if added, false otherwise.
   */
  public boolean addRule(Rule rule) {
    log.fine("Adding rewrite rule: "+rule);
    try {
      rewrite.addRule(rule);
      return true;
    } catch (Exception ex) {
      log.log(Level.WARNING, "Unable to add rewrite rule", ex);
    }
    return false;
  }

  /**
   * Builder style class for configuring web server options.
   */
  public static class WebServerOptions {
    protected String cacheControl = CACHE;
    protected boolean directoryListed = false;

    public WebServerOptions() {}

    public WebServerOptions cacheControl(String cacheControl) {
      this.cacheControl = cacheControl;
      return this;
    }

    public WebServerOptions directoryListed(boolean directoryListed) {
      this.directoryListed = directoryListed;
      return this;
    }
  }

  //////// private methods

  /**
   * Adds a context handler to the server. Context handler should be added before the web
   * server is started.
   *
   * @param handler context handler
   * @return true if added, false otherwise.
   */
  private boolean add(ContextHandler handler) {
    log.info("Adding web context: "+handler.getContextPath());
    contexts.addHandler(handler);
    try {
      handler.start();
    } catch (Exception ex) {
      log.log(Level.WARNING, "Unable to start context "+handler.getContextPath(), ex);
      return false;
    }
    return true;
  }

  /**
   * Removes a context handler.
   *
   * @param handler context handler to remove.
   */
  private boolean remove(ContextHandler handler) {
    log.info("Removing web context: "+handler.getContextPath());
    try {
      handler.stop();
    } catch (Exception ex) {
      log.log(Level.WARNING, "Unable to stop context "+handler.getContextPath(), ex);
      return false;
    }
    contexts.removeHandler(handler);
    return true;
  }



  /**
   * Handler for uploading files to the server.
   */
  private static class UploadHandler extends AbstractHandler {
    private final MultipartConfigElement multipartConfig;
    private final Path outputDir;

    public UploadHandler(MultipartConfigElement multipartConfig, Path outputDir) {
      super();
      this.multipartConfig = multipartConfig;
      if (!Files.exists(outputDir)){
        try {
          Files.createDirectories(outputDir);
        }catch (IOException ex){
          log.log(Level.WARNING, "Unable to create output directory : " + outputDir, ex);
        }
      }
      this.outputDir = outputDir;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException{
      if (!request.getMethod().equalsIgnoreCase("POST")){
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return;
      }
      request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, multipartConfig);
      response.setContentType("text/plain");
      response.setCharacterEncoding("utf-8");
      PrintWriter out = response.getWriter();
      for (Part part : request.getParts()) {
        String filename = part.getSubmittedFileName();
        if (StringUtil.isNotBlank(filename)){
          filename = URLEncoder.encode(filename, String.valueOf(StandardCharsets.UTF_8));
          Path outputFile = outputDir.resolve(filename);
          try (InputStream inputStream = part.getInputStream();
               FileOutputStream outputStream = new FileOutputStream(outputFile.toFile())) {
            IO.copy(inputStream, outputStream);
            // Force data and metadata to disk
            outputStream.getChannel().force(true);
            // Ensure lowest-level disk synchronization
            outputStream.getFD().sync();
            out.printf("%s%n", outputFile);
          }catch (Throwable ex){
            log.log(Level.WARNING, "Unable to save file : " + filename, ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            baseRequest.setHandled(true);
            return;
          }
        }
      }
      baseRequest.setHandled(true);
    }
  }

  /**
   * Context handler for serving Directory listing as plain text
   * instead of HTML. If the request is for a directory, and the content-type
   * is text/plain, the directory listing is returned as plain text, else the default
   * ResourceHandler is used.
   */
  private static class DirectoryHandler extends ResourceHandler {

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      if (baseRequest.isHandled()) return;
      File base = getBaseResource().getFile();
      if (base != null && target.endsWith("/")) {
        if (request.getContentType() != null && request.getContentType().equals("text/plain")) {
          String path = request.getPathInfo();
          if (path == null) path = "/";
          File dir = new File(base, path);
          if (dir.isDirectory()) {
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_OK);
            for (File f: dir.listFiles()) {
              if (f.isHidden()) continue;
              response.getWriter().println(f.getName()+" "+f.length()+" "+f.lastModified());
            }
            baseRequest.setHandled(true);
            return;
          }
        } else if (request.getContentType() != null && request.getContentType().equals("application/json")) {
          String path = request.getPathInfo();
          if (path == null) path = "/";
          File dir = new File(getBaseResource().getFile(), path);
          if (dir.isDirectory()) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().print("[");
            boolean first = true;
            for (File f: dir.listFiles()) {
              if (f.isHidden()) continue;
              if (!first) response.getWriter().print(",");
              response.getWriter().print("{\"name\":\""+f.getName()+"\",\"size\":"+f.length()+",\"date\":"+f.lastModified()+"}");
              first = false;
            }
            response.getWriter().print("]");
            baseRequest.setHandled(true);
            return;
          }
        }
      }
      super.handle(target, baseRequest, request, response);
    }
  }
}
