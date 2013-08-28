package sylvain.juge.memoryfs;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

public class MemoryFileSystemProvider extends FileSystemProvider {

    public static final String SCHEME = "memory";

    // thread safety : synchronized on instance for r/w
    private final Map<String, MemoryFileSystem> fileSystems;

    public MemoryFileSystemProvider() {
        this.fileSystems = new HashMap<>();
    }

    @Override
    public String getScheme() {
        return SCHEME;
    }

    private static void checkMemoryScheme(URI uri){
        if (!SCHEME.equals(uri.getScheme())) {
            throw new IllegalArgumentException("invalid scheme : " + uri);
        }
    }

    private static void checkFileSystemUri(URI uri){
        if(!uri.isAbsolute()){
            throw new IllegalArgumentException("invalid URI : must be absolute : " + uri);
        }
        String path = uri.getPath();
        if (!"/".equals(path)) {
            throw new IllegalArgumentException("invalid URI, fs root path must be / : " + uri);
        }
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        checkMemoryScheme(uri);
        checkFileSystemUri(uri);
        String id = getFileSystemIdentifier(uri);
        synchronized (fileSystems) {
            if (fileSystems.containsKey(id)) {
                throw new FileSystemAlreadyExistsException("file system already exists : " + id);
            }
            MemoryFileSystem fs = new MemoryFileSystem(this, id);
            fileSystems.put(id, fs);
            return fs;
        }
    }

    @Override
    public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
        URI uri = URI.create(String.format("%s://%s", SCHEME, path));
        return newFileSystem(uri, env);
    }

    private static String getFileSystemIdentifier(URI uri) {
        String host = uri.getHost();
        return null == host ? "": host;
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        checkMemoryScheme(uri);
        checkFileSystemUri(uri);
        String id = getFileSystemIdentifier(uri);
        synchronized (fileSystems) {
            FileSystem fs = fileSystems.get(id);
            if (null == fs) {
                throw new RuntimeException("no filesystem exists with this ID : " + id);
            }
            return fs;
        }
    }

    Map<String,? extends FileSystem> registeredFileSystems(){
        synchronized (fileSystems) {
            return Collections.unmodifiableMap(fileSystems);
        }
    }

    void removeFileSystem(String id) {
        synchronized (fileSystems) {
            if (!fileSystems.containsKey(id)) {
                throw new IllegalStateException("file system does not exist in provider : " + id);
            }
            fileSystems.remove(id);
        }
    }

    @Override
    public Path getPath(URI uri) {
        checkMemoryScheme(uri);
        String id = getFileSystemIdentifier(uri);
        synchronized (fileSystems){
            MemoryFileSystem fs = fileSystems.get(id);
            if( null == fs){
                throw new IllegalArgumentException("non existing filesystem : "+id);
            }
            return new MemoryPath(fs, uri.getPath());
        }
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
        // zip fs : delegate to path implementation
    }

    @Override
    public void delete(Path path) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
        // zip fs : delegate to path implementation
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
        // zip fs : delegate to path implementation
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
        // zip fs : delegate to path implementation
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        throw new UnsupportedOperationException("TODO : implement this");
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
    }
}
