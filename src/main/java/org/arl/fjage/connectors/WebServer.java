/******************************************************************************

 Copyright (c) 2013, Mandar Chitre

 This file is part of fjage which is released under Simplified BSD License.
 See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
 for full license details.

 ******************************************************************************/

package org.arl.fjage.connectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.jetty.compression.server.CompressionConfig;
import org.eclipse.jetty.compression.server.CompressionHandler;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MultiPartConfig;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.Rule;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.thread.Invocable.InvocationType;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.websocket.server.WebSocketCreator;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;

import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

  private static final java.util.logging.Logger jettyLog = java.util.logging.Logger.getLogger("org.eclipse.jetty");

  static {
    jettyLog.setLevel(Level.WARNING);
  }

  /**
   * Gets an instance of a web server running on the specified port. If an instance is not
   * already available, a new one is created and started.
   *
   * @param port HTTP port number.
   * @throws UncheckedIOException if a new web server cannot be started (e.g. port already in use).
   */
  public static WebServer getInstance(int port) {
    return getInstance(port, "127.0.0.1");
  }

  /**
   * Gets an instance of a web server running on the specified port. If an instance is not
   * already available, a new one is created and started.
   *
   * @param port HTTP port number.
   * @param ip IP address to bind HTTP server to.
   * @throws UncheckedIOException if a new web server cannot be started (e.g. port already in use).
   */
  public static WebServer getInstance(int port, String ip) {
    synchronized (servers) {
      WebServer svr = servers.get(port);
      if (svr == null || svr.server.isStopped()) svr = new WebServer(port, ip);
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
      return servers.values().toArray(WebServer[]::new);
    }
  }

  /**
   * Shutdown all web servers.
   */
  public static void shutdown() {
    for (WebServer svr : getInstances()) svr.stop();
  }

  //////// instance attributes and methods

  protected final Server server;
  protected final ContextHandlerCollection contexts;
  protected final RewriteHandler rewrite;
  protected volatile ErrorHandler defaultErrorHandler;
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
   * <code> CompressionHandler -> RewriteHandler -> Sequence[ ContextHandlerCollection, DefaultHandler ] </code>
   * </p>
   * Any new handlers added to the server will be added to the list of ContextHandlers (contexts).
   * The server is started right away, and handlers and rules may be added to it while it runs.
   *
   * @param port HTTP port number.
   * @param ip IP address to bind HTTP server to.
   * @throws UncheckedIOException if the server cannot be started (e.g. port already in use).
   */
  protected WebServer(int port, String ip) {
    this.port = port;
    server = new Server(InetSocketAddress.createUnresolved(ip, port));
    server.setStopAtShutdown(true);
    rewrite = new RewriteHandler();
    contexts = new ContextHandlerCollection();
    CompressionHandler compression = new CompressionHandler();
    CompressionConfig.Builder config = CompressionConfig.builder().compressIncludeEncoding("gzip");
    for (String mimeType : List.of("text/html", "text/plain", "text/xml", "text/css", "application/javascript", "text/javascript"))
      config.compressIncludeMimeType(mimeType);
    compression.putConfiguration("/*", config.build());
    compression.setHandler(rewrite);
    rewrite.setHandler(new Handler.Sequence(contexts, new DefaultHandler()));
    server.setHandler(compression);
    ThreadPool pool = server.getThreadPool();
    if (pool instanceof QueuedThreadPool threadPool) threadPool.setDaemon(true);
    try {
      server.start();
    } catch (Exception ex) {
      try {
        server.stop();
      } catch (Exception ignored) {
        // best effort cleanup of a server that never started
      }
      // name the most common cause explicitly
      String msg = isPortInUse(ex) ? "Unable to start web server: port "+port+" is already in use" : "Unable to start web server on port "+port;
      throw new UncheckedIOException(msg, ex instanceof IOException io ? io : new IOException(msg, ex));
    }
    log.info("Started web server on port "+port);
    if (port > 0) {
      synchronized (servers) { servers.put(port, this); }
    }
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
   * Starts the web server. The web server is started when it is created, so this method
   * does nothing, and is kept for backward compatibility.
   *
   * @deprecated the web server is started by {@link #getInstance(int)}.
   */
  @Deprecated
  public void start() {
    // already started
  }

  /**
   * Checks if an exception was caused by a port already being in use.
   *
   * @param ex exception to check.
   * @return true if caused by a bind failure, false otherwise.
   */
  private static boolean isPortInUse(Throwable ex) {
    while (ex != null) {
      if (ex instanceof BindException) return true;
      ex = ex.getCause();
    }
    return false;
  }

  /**
   * Stops the web server. Once this method is called, the server cannot be restarted.
   */
  public void stop() {
    try {
      server.stop();
      if (port > 0) {
        synchronized (servers) { servers.remove(port, this); }
      }
    } catch (Exception ex) {
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      log.log(Level.WARNING, "Unable to stop web server", ex);
    }
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
    checkContext(context);
    if (resource == null || resource.isEmpty()) throw new IllegalArgumentException("Resource cannot be null or empty");
    if(resource.startsWith("/")) resource = resource.substring(1);
    ArrayList<URL> res = new ArrayList<>();
    try {
      res = Collections.list(getClass().getClassLoader().getResources(resource));
    }catch (IOException ex){
      // do nothing
    }
    List<ContextHandler> handlers = new ArrayList<>();
    if (options.directoryListed) log.warning("Directory listing is not supported for resources in jars");
    for (URL r : res) {
      ContextHandler handler = addStatic(context, r.toExternalForm(), options.cacheControl, false);
      if (handler != null) handlers.add(handler);
    }
    if(!handlers.isEmpty()) log.info("Adding static handler at "+context+" -> :"+resource);
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
    checkContext(context);
    if (dir == null || !dir.exists()) throw new IllegalArgumentException("Directory cannot be null and must exist");
    try {
      ContextHandler handler = addStatic(context, dir.getCanonicalPath(), options.cacheControl, options.directoryListed);
      if (handler != null) {
        log.info("Adding static handler at "+context+" -> "+dir);
        return List.of(handler);
      }
    }catch (IOException ex){
      log.log(Level.WARNING, "Unable to add context : " + context, ex);
    }
    return List.of();
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
   * The maximum file size and maximum request size are set to 1 GB. The file
   * size threshold is set to 100 MB, after which files will be written to disk
   * temporarily instead of kept in memory during processing. The directory to
   * which the file is written temporarily is the same as the upload directory.
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
   * For files exceeding the file size threshold, the directory to which the
   * file is written temporarily is the same as the upload directory.
   *
   * @param context context path.
   * @param dir filesystem path of directory to upload files to.
   * @param maxFileSize maximum size of a file.
   * @param maxRequestSize maximum size of a request.
   * @param fileSizeThreshold size threshold after which files will be written to disk.
   * @return true if added, false otherwise.
   */
  public boolean addUpload(String context, File dir, long maxFileSize, long maxRequestSize, int fileSizeThreshold) {
    String location = dir.getAbsolutePath();
    return addUpload(context, dir, location, maxFileSize, maxRequestSize, fileSizeThreshold);
  }

  /**
   * Adds a context to upload files to.
   *
   * @param context context path.
   * @param dir filesystem path of directory to upload files to.
   * @param tmpLocation filesystem path of directory where temporary files will be stored.
   * @param maxFileSize maximum size of a file.
   * @param maxRequestSize maximum size of a request.
   * @param fileSizeThreshold size threshold after which files will be written to disk.
   * @return true if added, false otherwise.
   */
  public boolean addUpload(String context, File dir, String tmpLocation, long maxFileSize, long maxRequestSize, int fileSizeThreshold) {
    checkContext(context);
    Path tmpDir = tmpLocation == null || tmpLocation.isEmpty() ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(tmpLocation);
    MultiPartConfig multipartConfig = new MultiPartConfig.Builder().location(tmpDir)
        .maxPartSize(maxFileSize == 0 ? -1 : maxFileSize).maxSize(maxRequestSize == 0 ? -1 : maxRequestSize).maxMemoryPartSize(fileSizeThreshold).build();
    ContextHandler handler = new ContextHandler(context);
    handler.setAllowNullPathInContext(true);
    handler.setHandler(new UploadHandler(multipartConfig, dir.toPath()));
    if (add(handler)) {
      log.info("Adding upload handler at " + context + " -> " + dir.getPath());
      return true;
    }
    return false;
  }

  /**
   * Add a handler to the server at the specified context.
   *
   * @param context context path.
   * @param handler handler to add.
   * @return ContextHandler object if added, null otherwise (e.g. context already in use).
   */
  public ContextHandler addHandler(String context, Handler handler) {
    checkContext(context);
    if (handler == null) throw new IllegalArgumentException("Handler cannot be null");
    if (hasHandler(context)) {
      log.warning("Context "+context+" already in use on port "+port);
      return null;
    }
    ContextHandler c = new ContextHandler(context);
    // WebSocket upgrades cannot follow redirects.
    c.setAllowNullPathInContext(true);
    c.setHandler(handler);
    if (add(c, true)) return c;
    return null;
  }

  /**
   * Adds a native WebSocket endpoint at the specified context.
   *
   * @param context context path.
   * @param creator WebSocket endpoint creator.
   * @param maxMsgSize maximum text message size, or a nonpositive value for Jetty's default.
   * @return context handler if added, null otherwise.
   */
  public ContextHandler addWebSocket(String context, WebSocketCreator creator, int maxMsgSize) {
    if (creator == null) throw new IllegalArgumentException("Creator cannot be null");
    checkContext(context);
    if (!server.isStarted() || hasHandler(context)) return null;
    ContextHandler handler = new ContextHandler(context);
    handler.setAllowNullPathInContext(true);
    handler.setHandler(WebSocketUpgradeHandler.from(server, handler, container -> {
      container.setIdleTimeout(Duration.ofMillis(Integer.MAX_VALUE));
      if (maxMsgSize > 0) container.setMaxTextMessageSize(maxMsgSize);
      container.addMapping("/*", creator);
    }));
    return add(handler, true) ? handler : null;
  }

  /**
   * Checks if a handler is already registered for the specified context.
   *
   * @param context context path.
   * @return true if a handler is already registered, false otherwise.
   */
  public boolean hasHandler(String context) {
    return handlers().stream().anyMatch(h -> h instanceof ContextHandler c && c.getContextPath().equals(context));
  }

  /**
   * Gets the registered context handlers, or an empty list after shutdown.
   */
  private List<Handler> handlers() {
    return server.isStopped() ? List.of() : contexts.getHandlers();
  }

  /**
   * Removes a ContextHandler from the server.
   *
   * @param handler handler to remove.
   * @return true if removed, false otherwise.
   */
  public boolean removeHandler(ContextHandler handler) {
    if (handler == null) throw new IllegalArgumentException("Handler cannot be null");
    if (remove(handler)) {
      log.info("Removing handler for "+handler.getContextPath());
      return true;
    }
    return false;
  }

  /**
   * Sets a global error handler for all context handlers on the server.
   *
   * Any contexts added subsequently will automatically adopt this error handler.
   * Note that this will override any error handlers that have already been set for existing contexts.
   *
   * @param errorHandler error handler to set.
   */
  public void setErrorHandler(ErrorHandler errorHandler) {
    if (errorHandler == null) throw new IllegalArgumentException("Error handler cannot be null");
    this.defaultErrorHandler = errorHandler;
    for (Handler h : handlers()) {
      if (h instanceof ContextHandler contextHandler) {
        contextHandler.setErrorHandler(errorHandler);
      }
    }
  }

  /**
   * Sets an error handler for a specific context path.
   *
   * Note that this will override any error handler that has already been set for the specified context.
   *
   * @param context context path.
   * @param errorHandler error handler to set.
   * @return true if applied to at least one matching context handler, false otherwise.
   */
  public boolean setErrorHandler(String context, ErrorHandler errorHandler) {
    checkContext(context);
    if (errorHandler == null) throw new IllegalArgumentException("Error handler cannot be null");
    boolean updated = false;
    for (Handler h : handlers()) {
      if (h instanceof ContextHandler contextHandler && contextHandler.getContextPath().equals(context)) {
        contextHandler.setErrorHandler(errorHandler);
        updated = true;
      }
    }
    return updated;
  }

  /**
   * Adds a rule to rewrite handler.
   *
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

  private static void checkContext(String context) {
    if (context == null || context.isEmpty()) throw new IllegalArgumentException("Context cannot be null or empty");
    if (!context.startsWith("/")) throw new IllegalArgumentException("Context must start with '/'");
  }

  private ContextHandler addStatic(String context, String resource, String cacheControl, boolean directoryListed) {
    ContextHandler handler = new StaticContextHandler(context);
    ResourceHandler content = directoryListed ? new DirectoryHandler() : new ResourceHandler();
    var resources = ResourceFactory.of(content);
    boolean published = false;
    try {
      content.setBaseResource(resources.newResource(resource));
      content.setWelcomeFiles(new String[] {"index.html"});
      content.setDirAllowed(directoryListed);
      content.setCacheControl(cacheControl);
      content.setEtags(true);
      handler.setHandler(content);
      published = add(handler);
      return published ? handler : null;
    } finally {
      if (!published) LifeCycle.stop(resources);
    }
  }

  private boolean add(ContextHandler handler) {
    return add(handler, false);
  }

  private boolean add(ContextHandler handler, boolean exclusive) {
    ContextHandlerCollection collection = contexts;
    Server owner = server;
    if (!owner.isStarted()) return false;
    if (defaultErrorHandler != null) handler.setErrorHandler(defaultErrorHandler);
    try {
      handler.setServer(owner);
      collection.addManaged(handler);
      synchronized (collection) {
        if (!owner.isStarted()) throw new IllegalStateException("Server is stopping");
        if (exclusive && hasHandler(handler.getContextPath()))
          throw new IllegalStateException("Context already in use: "+handler.getContextPath());
        collection.addHandler(handler);
      }
      return true;
    } catch (Exception ex) {
      synchronized (collection) {
        if (collection.contains(handler)) collection.unmanage(handler);
        collection.removeHandler(handler);
        collection.removeBean(handler);
      }
      try {
        handler.stop();
      } catch (Exception cleanup) {
        ex.addSuppressed(cleanup);
      }
      try {
        handler.destroy();
      } catch (Exception cleanup) {
        ex.addSuppressed(cleanup);
      }
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      log.log(Level.WARNING, "Unable to start context "+handler.getContextPath(), ex);
      return false;
    }
  }

  /**
   * Removes a context handler.
   *
   * @param handler context handler to remove.
   */
  private boolean remove(ContextHandler handler) {
    ContextHandlerCollection collection = contexts;
    try {
      synchronized (collection) {
        if (!collection.getHandlers().contains(handler)) return false;
        collection.unmanage(handler);
        collection.removeHandler(handler);
      }
      // Cleanup may manage other contexts.
      try {
        handler.stop();
      } finally {
        handler.destroy();
      }
      return true;
    } catch (Exception ex) {
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      log.log(Level.WARNING, "Unable to remove context "+handler.getContextPath(), ex);
      return false;
    }
  }

  /** Keeps static directory redirects temporary, as in the previous server. */
  private static class StaticContextHandler extends ContextHandler {
    StaticContextHandler(String context) { super(context); }

    @Override
    protected void handleMovedPermanently(Request request, Response response, Callback callback) {
      String location = getContextPath()+"/";
      if (request.getHttpURI().getParam() != null) location += ";"+request.getHttpURI().getParam();
      if (request.getHttpURI().getQuery() != null) location += "?"+request.getHttpURI().getQuery();
      response.setStatus(302);
      response.getHeaders().put(HttpHeader.LOCATION, location);
      callback.succeeded();
    }
  }

  /** Handler for multipart file uploads. Disk work is dispatched as blocking work. */
  private static class UploadHandler extends Handler.Abstract {
    private final MultiPartConfig multipartConfig;
    private final Path outputDir;

    UploadHandler(MultiPartConfig multipartConfig, Path outputDir) {
      this.multipartConfig = multipartConfig;
      this.outputDir = outputDir;
    }

    @Override
    protected void doStart() throws Exception {
      Files.createDirectories(outputDir);
      Files.createDirectories(multipartConfig.getLocation());
      super.doStart();
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) {
      if (!"POST".equalsIgnoreCase(request.getMethod())) {
        Response.writeError(request, response, callback, 405);
        return true;
      }
      String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
      MultiPartFormData.onParts(request, request, contentType, multipartConfig, new Promise.Invocable<>() {
        @Override
        public InvocationType getInvocationType() {
          return InvocationType.BLOCKING;
        }

        @Override
        public void succeeded(MultiPartFormData.Parts parts) {
          StringBuilder result = new StringBuilder();
          try (parts) {
            Files.createDirectories(outputDir);
            for (var part : parts) {
              String filename = part.getFileName();
              if (StringUtil.isBlank(filename)) continue;
              Path outputFile = outputDir.resolve(URLEncoder.encode(filename, StandardCharsets.UTF_8));
              try (InputStream input = Content.Source.asInputStream(part.getContentSource());
                   FileOutputStream output = new FileOutputStream(outputFile.toFile())) {
                input.transferTo(output);
                output.getChannel().force(true);
              }
              result.append(outputFile).append('\n');
            }
          } catch (Exception ex) {
            log.log(Level.WARNING, "Unable to save uploaded file", ex);
            Response.writeError(request, response, callback, 500);
            return;
          }
          response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
          Content.Sink.write(response, true, result.toString(), callback);
        }

        @Override
        public void failed(Throwable failure) {
          Response.writeError(request, response, callback, 400, "Invalid multipart upload", failure);
        }
      });
      return true;
    }
  }

  /** Serves plain text or JSON directory listings, with ResourceHandler as the fallback. */
  private static class DirectoryHandler extends ResourceHandler {
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
      String path = Request.getPathInContext(request);
      String type = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
      if (path != null && path.endsWith("/") && ("text/plain".equals(type) || "application/json".equals(type))) {
        var resource = getBaseResource().resolve(path);
        ContextHandler context = ContextHandler.getContextHandler(request);
        if (resource.isDirectory() && context.checkAlias(path, resource)) {
          StringBuilder text = new StringBuilder();
          JsonArray json = new JsonArray();
          for (var child : resource.list()) {
            Path file = child.getPath();
            if (file == null || Files.isHidden(file)) continue;
            String name = child.getFileName();
            long size = child.length();
            long date = child.lastModified().toEpochMilli();
            if ("text/plain".equals(type)) text.append(name).append(' ').append(size).append(' ').append(date).append('\n');
            else {
              JsonObject entry = new JsonObject();
              entry.addProperty("name", name);
              entry.addProperty("size", size);
              entry.addProperty("date", date);
              json.add(entry);
            }
          }
          response.getHeaders().put(HttpHeader.CONTENT_TYPE, type+";charset=utf-8");
          Content.Sink.write(response, true, "text/plain".equals(type) ? text.toString() : json.toString(), callback);
          return true;
        }
      }
      return super.handle(request, response, callback);
    }
  }
}
