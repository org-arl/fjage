package org.arl.fjage.persistence;

import java.io.*;
import java.util.*;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.io.FileUtils;
import org.arl.fjage.Agent;
import org.arl.fjage.FjageException;

/**
 * Persistent storage API. The default store is in the user's home directory under
 * a folder called ".fjstore", but may be changed using the {@link #setRoot(java.io.File)} method.
 */
public class Store implements Closeable {

  protected static File storeRoot = new File(FileUtils.getUserDirectory(), ".fjstore");
  protected static ClassLoader defaultClazzLoader = null;
  protected static final Map<String,Store> instances = new HashMap<>();

  protected File root;
  protected String clazz;
  protected ClassLoader clazzLoader;
  private final MessageDigest md;

  protected Store(String clazz) {
    this.clazz = clazz;
    clazzLoader = defaultClazzLoader;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException ex) {
      throw new FjageException("SHA-256 not available");
    }
    root = new File(storeRoot, clazz);
  }

  /**
   * Sets root folder for store data.
   */
  public static void setRoot(File folder) {
    storeRoot = folder;
  }

  /**
   * Sets class loader to use for loading stored classes.
   *
   * @param cl class loader to use, or null to use default
   */
  public static void setClassLoader(ClassLoader cl) {
    defaultClazzLoader = cl;
  }

  /**
   * Gets a store instance for an agent.
   */
  public static Store getInstance(Agent agent) {
    String clazz = agent.getClass().getName();
    synchronized (instances) {
      Store store = instances.get(clazz);
      if (store == null) {
        store = new Store(clazz);
        instances.put(clazz, store);
      }
      return store;
    }
  }

  private String sha(byte[] input) {
    synchronized (md) {
      byte[] digest = md.digest(input);
      BigInteger num = new BigInteger(1, digest);
      return num.toString(16);
    }
  }

  /**
   * Gets the ID for an object. If a getter "getId()" exists, it is used to get
   * the ID of the object. Otherwise a SHA-256 hash of the serialized object
   * content is used as an ID.
   */
  protected String getId(Serializable obj) {
    Class<?> cls = obj.getClass();
    try {
      Method m = cls.getMethod("getId");
      return m.invoke(obj).toString();
    } catch (Exception ex) {
      // ignore, and try next approach
    }
    ByteArrayOutputStream baos = null;
    ObjectOutputStream oos = null;
    try {
      baos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(baos);
      oos.writeObject(obj);
      return sha(baos.toByteArray());
    } catch (Exception ex) {
      return String.valueOf(obj.hashCode());
    } finally {
      closeQuietly(oos);
      closeQuietly(baos);
    }
  }

  /**
   * Stores an object.
   */
  public void put(Serializable obj) {
    if (root == null) throw new FjageException("Store has been closed");
    File d = new File(root, obj.getClass().getName());
    if (!d.mkdirs() && !d.exists()) throw new FjageException("Cannot create directory " + d);
    File f = new File(d, getId(obj));
    FileOutputStream fout = null;
    ObjectOutputStream out = null;
    try {
      fout = new FileOutputStream(f);
      out = new ObjectOutputStream(fout);
      out.writeObject(obj);
      out.flush();
      fout.getFD().sync();
    } catch (Exception ex) {
      throw new FjageException(ex.toString());
    } finally {
      closeQuietly(out);
      closeQuietly(fout);
    }
  }

  /**
   * Loads a stored object from a file.
   */
  protected <T> T load(Class<T> type, File f) {
    FileInputStream fin = null;
    ObjectInputStream in = null;
    try {
      fin = new FileInputStream(f);
      if (clazzLoader == null) in = new ObjectInputStream(fin);
      else in = new ObjectInputStream(fin) {
        @Override
        protected Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
            return Class.forName(objectStreamClass.getName(), false, clazzLoader);
        }
      };
      @SuppressWarnings("unchecked")
      T rv = (T) in.readObject();
      return rv;
    } catch (IOException ex) {
      return null;
    } catch (Exception ex) {
      throw new FjageException(ex.toString());
    } finally {
      closeQuietly(in);
      closeQuietly(fin);
    }
  }

  /**
   * Gets an object in the store.
   *
   * @param type object class
   * @param id object ID
   * @return object with given ID, or null if not found
   */
  public <T> T getById(Class<T> type, String id) {
    if (root == null) throw new FjageException("Store has been closed");
    File f = new File(new File(root, type.getName()), id);
    return load(type, f);
  }

  /**
   * Gets all objects of a given class in the store.
   *
   * @param type object class
   * @return list of objects with specified class
   */
  public <T> List<T> getByType(Class<T> type) {
    if (root == null) throw new FjageException("Store has been closed");
    List<T> out = new ArrayList<T>();
    File[] list = new File(root, type.getName()).listFiles();
    if (list == null) return out;
    for (File file : list) {
        T obj = load(type, file);
        if (obj != null) out.add(obj);
    }
    return out;
  }

  /**
   * Removes an object from the store.
   *
   * @param obj object to remove
   * @return true if removed, false otherwise
   */
  public boolean remove(Serializable obj) {
    return removeById(obj.getClass(), getId(obj));
  }

  /**
   * Removes an object from the store.
   *
   * @param type object class
   * @param id object ID to remove
   * @return true if removed, false otherwise
   */
  public <T> boolean removeById(Class<T> type, String id) {
    if (root == null) throw new FjageException("Store has been closed");
    File f = new File(new File(root, type.getName()), id);
    return f.delete();
  }

  /**
   * Removes all objects of a given type from the store.
   *
   * @param type objet class
   * @return true if removed, false otherwise
   */
  public <T> boolean removeByType(Class<T> type) {
    if (root == null) throw new FjageException("Store has been closed");
    try {
      File f = new File(root, type.getName());
      FileUtils.deleteDirectory(f);
      return true;
    } catch (IOException ex) {
      return false;
    }
  }

  /**
   * Deletes the data store. All objects stored are removed. Once this method
   * is called, the store is deleted and closed. The instance of the store should
   * no longer be used.
   */
  public boolean delete() {
    if (root == null) throw new FjageException("Store has been closed");
    try {
      FileUtils.deleteDirectory(root);
      close();
      return true;
    } catch (IOException ex) {
      return false;
    }
  }

  /**
   * Gets the size of the data store (in bytes).
   */
  public long size() {
    if (root == null) throw new FjageException("Store has been closed");
    try {
      return FileUtils.sizeOfDirectory(root);
    } catch (IllegalArgumentException | UncheckedIOException ex) {
      return 0;
    }
  }

  /**
   * Closes the store. The instance of the store should no longer be used.
   */
  @Override
  public void close() {
    if (root == null) return;
    synchronized (instances) {
      instances.remove(clazz);
    }
    clazz = null;
    root = null;
  }

  /**
   * Closes input stream quietly.
   */
  protected static void closeQuietly(InputStream is) {
    if (is == null)
      return;
    try {
      is.close();
    } catch (IOException ex) {
      // do nothing
    }
  }

  /**
   * Closes output stream quietly.
   */
  protected static void closeQuietly(OutputStream os) {
    if (os == null)
      return;
    try {
      os.close();
    } catch (IOException ex) {
      // do nothing
    }
  }

}
