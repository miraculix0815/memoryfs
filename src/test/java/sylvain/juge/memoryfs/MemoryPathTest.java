package sylvain.juge.memoryfs;

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.fest.assertions.api.Assertions.assertThat;
import static sylvain.juge.memoryfs.TestEquals.checkHashCodeEqualsConsistency;

public class MemoryPathTest {

    private static final MemoryFileSystem defaultFs = createFs();

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void fileSystemRequired() {
        MemoryPath.create(null, "/anypath");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void emptyPathNotAllowed() {
        createPath("");
    }

    @Test
    public void getFileSystem() {
        MemoryFileSystem fs = createFs();
        MemoryPath path = MemoryPath.create(fs, "/");
        assertThat(path.getFileSystem()).isSameAs(fs);
    }

    @Test
    public void relativePathToUri() {
        MemoryPath path = createPath("relative/path");

        assertThat(path.isAbsolute()).isFalse();
        assertThat(path.getRoot()).isNull(); // relative path does not have root
        // TODO : get relative path parent

        checkParts(path,
                "relative",
                "relative/path");
    }


    @Test
    public void absolutePathToUri() {
        MemoryPath path = createPath("/absolute/path");

        assertThat(path.isAbsolute()).isTrue();
        assertThat(path.getRoot()).isEqualTo(createPath("/"));

        checkParts(path,
                "/absolute",
                "/absolute/path");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void getNameLessThanZero() {
        createPath("/").getName(-1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void getNameOutOfIndex() {
        MemoryPath path = createPath("/dummy");
        assertThat(path.getNameCount()).isEqualTo(1);
        path.getName(path.getNameCount());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void getNameEmptyParts() {
        MemoryPath path = createPath("/");
        assertThat(path.getNameCount()).isZero();
        path.getName(0);
    }

    @Test
    public void getFileName() {
        // like default FS, root path is an "empty absolute path"
        checkFileName(createPath("/"), null);
        checkFileName(createPath("/a"), "a");
        checkFileName(createPath("/a/b"), "b");
        checkFileName(createPath("a"), "a");
        checkFileName(createPath("a/b"), "b");
    }

    @Test
    public void absolutePath() {
        for (String absolute : Arrays.asList("/", "/a", "/a/b")) {
            MemoryPath path = createPath(absolute);
            assertThat(path.isAbsolute()).isTrue();
            assertThat(path.toAbsolutePath()).isEqualTo(path);
        }
        for (String relative : Arrays.asList("a", "a/b")) {
            MemoryPath path = createPath(relative);
            assertThat(path.isAbsolute()).isFalse();
            Path toAbsolute = path.toAbsolutePath();
            assertThat(toAbsolute.isAbsolute()).isTrue();
            // TODO : check that resuling path endsWith 'relative' as suffix
            assertThat(toAbsolute.toUri().toString()).isEqualTo("memory:/" + relative);
        }
        // TODO : relative path without normalization
    }

    @Test
    public void pathWithTrailingSlash() {
        // useless trailing slash should be removed from path
        MemoryPath path = createAndCheckPath("/withTrailingSlash/", "/withTrailingSlash");
        assertThat(path.getNameCount()).isEqualTo(1);
        assertThat(path).isEqualTo(createPath("/withTrailingSlash"));
    }

    @Test
    public void rootPath() {
        MemoryPath root = createPath("/");
        assertThat(root.getNameCount()).isEqualTo(0);
        assertThat(root.getParent()).isNull(); // TODO : does parent of root is null or is it itself ?
        assertThat(root.getRoot()).isEqualTo(root);
    }

    @Test
    public void pathEqualityWorksOnlyWithIdenticalFileSystemInstance() {
        // similar paths but not in the same file system are not equal
        String path = "/a/b/c";
        MemoryFileSystem fs1 = createFs();
        MemoryFileSystem fs2 = createFs();
        MemoryPath path1 = MemoryPath.create(fs1, path);
        MemoryPath path2 = MemoryPath.create(fs2, path);
        assertThat(path1.getPath()).isEqualTo(path2.getPath());
        assertThat(path1.getFileSystem()).isNotSameAs(path2.getFileSystem());
        assertThat(path1).isNotEqualTo(path2);
        assertThat(path1.hashCode()).isNotEqualTo(path2.hashCode());
    }


    @Test
    public void equalsHashCodeWithItself() {
        Path p;
        p = createPath("/absolute");
        checkHashCodeEqualsConsistency(true, p, p);
        p = createPath("relative");
        checkHashCodeEqualsConsistency(true, p, p);
    }

    @Test
    public void equalsHashCodeWithSamePartsButAbsoluteness() {
        Path p1 = createPath("same/path");
        assertThat(p1.isAbsolute()).isFalse();
        Path p2 = createPath("/same/path");
        assertThat(p2.isAbsolute()).isTrue();
        checkHashCodeEqualsConsistency(false, p1, p2);
    }

    @Test
    public void equalsHashCodeWithEquivalent() {
        checkHashCodeEqualsConsistency(true,
                createPath("/"),
                createPath("///"),
                createPath("//"));
        checkHashCodeEqualsConsistency(true,
                createPath("/a"),
                createPath("/a/"),
                createPath("/a//"),
                createPath("//a"));
        checkHashCodeEqualsConsistency(true,
                createPath("a/b"),
                createPath("a/b/"),
                createPath("a//b"));
    }

    @Test
    public void equalsHashCodeWithDifferent() {
        checkHashCodeEqualsConsistency(false,
                createPath("/a"),
                createPath("/b"));
        checkHashCodeEqualsConsistency(false,
                createPath("a"),
                createPath("b"));
    }

    @Test
    public void normalizeNormalizedOrNonNormalizablePaths() {
        for (String s : Arrays.asList("/a", "/a/b", "a", "a/b", "..", ".", "../a", "../..", "../../..")) {
            checkAlreadyNormalized(s, s);
        }
    }

    @Test
    public void normalizeNormalizablePaths() {
        checkAlreadyNormalized("/a/../b", "/b");
        checkAlreadyNormalized("/./a", "/a");
        checkAlreadyNormalized("a/../b/../c", "c");
        checkAlreadyNormalized("a/./b/.", "a/b");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void outOfRootAbsolutePath() {
        // thou shall not go upper than root !
        createPath("/..");
    }

    private final static List<MemoryPath> samplePaths;

    static {
        samplePaths = new ArrayList<>();
        List<String> paths = Arrays.asList("/", "/a", "/a/b", "a", "a/b", "..", ".", "../a", "../..", "../../..");
        for (String path : paths) {
            samplePaths.add(createPath(path));
        }
    }

    @Test
    public void startsAndEndsWithItself() {
        for (MemoryPath p : samplePaths) {
            String msg = String.format("%s should start with %s", p, p);
            assertThat(p.startsWith(p)).describedAs(msg).isTrue();
            assertThat(p.startsWith(p.getPath())).describedAs(msg).isTrue();
            msg = String.format("%s should end with %s", p, p);
            assertThat(p.endsWith(p)).describedAs(msg).isTrue();
            assertThat(p.endsWith(p.getPath())).describedAs(msg).isTrue();
        }
    }

    @Test
    public void absolutePathStartsWithRoot() {
        String rootPath = "/";
        Path root = createPath(rootPath);
        for (MemoryPath p : samplePaths) {
            if (p.isAbsolute()) {
                assertThat(p.startsWith(root)).isTrue();
                assertThat(p.startsWith(rootPath)).isTrue();
            }
        }
    }

    @Test
    public void startsWithPrefixPaths() {
        for (MemoryPath p : samplePaths) {
            for (int i = 0; i < p.getNameCount(); i++) {
                MemoryPath prefix = (MemoryPath) p.getName(i);
                assertThat(p.startsWith(prefix)).isTrue();
                assertThat(p.startsWith(prefix.getPath())).isTrue();
            }
        }
    }

    @Test
    public void startsWithSameElementsButAbsoluteness() {
        MemoryPath absoluteAbc = createPath("/a/b/c");
        MemoryPath relativeAbc = createPath("a/b/c");
        assertThat(absoluteAbc.startsWith(relativeAbc)).isFalse();
        assertThat(absoluteAbc.startsWith(relativeAbc.getPath())).isFalse();
    }

    @Test
    public void endsWithSameElementsButAbsoluteness() {
        MemoryPath absoluteAbc = createPath("/a/b/c");
        MemoryPath relativeAbc = createPath("a/b/c");
        assertThat(absoluteAbc.endsWith(relativeAbc)).isTrue();
        assertThat(absoluteAbc.endsWith(relativeAbc.getPath())).isTrue();
    }

    @Test
    public void startsWithStringPrefix() {
        // a/bc/d starts with a/b (which is not true for paths)
        MemoryPath abcd = createPath("a/bc/d");
        MemoryPath ab = createPath("a/b");
        assertThat(abcd.startsWith(ab)).isFalse();
        assertThat(abcd.startsWith(ab.getPath())).isTrue();
    }

    @Test
    public void endsWithStringPrefix() {
        // a/bc/d ends with c/d (which is not true for paths)
        MemoryPath abcd = createPath("a/bc/d");
        MemoryPath cd = createPath("c/d");
        assertThat(abcd.endsWith(cd)).isFalse();
        assertThat(abcd.endsWith(cd.getPath())).isTrue();
    }

    @Test
    public void startsAndEndsWithDoesNotNormalize() {
        // Note : we take absolute paths otherwise second paths ends with a
        MemoryPath a = createPath("/a");
        MemoryPath bDotDotA = createPath("/b/../a");
        assertThat(bDotDotA.normalize()).isEqualTo(a);
        assertThat(bDotDotA.startsWith(a)).isFalse();
        assertThat(bDotDotA.endsWith(a)).isFalse();
    }

    @Test(expectedExceptions = ProviderMismatchException.class)
    public void startsWithWrongPathType() {
        createPath("/").startsWith(anotherPathType());
    }

    @Test(expectedExceptions = ProviderMismatchException.class)
    public void endsWithWrongPathType() {
        createPath("/").endsWith(anotherPathType());
    }

    @Test(expectedExceptions = ProviderMismatchException.class)
    public void resolveWithWrongPathType() {
        createPath("/").resolve(anotherPathType());
    }

    @Test(expectedExceptions = ProviderMismatchException.class)
    public void resolveSiblingWithWrongPathType() {
        createPath("/").resolve(anotherPathType());
    }

    @Test(expectedExceptions = ProviderMismatchException.class)
    public void relativizeWithWrongPathType() {
        createPath("/").relativize(anotherPathType());
    }

    @Test(expectedExceptions = ClassCastException.class)
    public void compareToWithWrongPathType() {
        createPath("/").compareTo(anotherPathType());
    }

    @Test
    public void compareWithItselfAndEqualInstance() {
        MemoryPath path = createPath("/a/b/c");
        assertThat(path.compareTo(path)).isEqualTo(0);

        MemoryPath other = createPath(path.getPath());
        assertThat(path.compareTo(other)).isEqualTo(0);
    }

    @Test
    public void compareToUsesNaturalOrder() {
        checkCompareToStrictOrder("/a", "/b", "/c");
    }

    @Test
    public void compareToAbsolutesFirst() {
        checkCompareToStrictOrder("/a", "/b", "/c", "a", "b", "c");
    }

    @Test
    public void compareToDepthFirstOrdering() {
        // sub-paths of X are before paths that are after X
        checkCompareToStrictOrder("a", "a/b", "ab");
    }

    @Test
    public void compareToShortestFirst() {
        checkCompareToStrictOrder("a/b", "a/b/c");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void watchRegisterNotSupported1() throws IOException {
        createPath("/").register(null, new WatchEvent.Kind[0]);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void watchRegisterNotSupported2() throws IOException {
        createPath("/").register(null, null, new WatchEvent.Modifier[0]);
    }

    private static void checkCompareToStrictOrder(String... paths) {
        if (paths.length < 2) {
            throw new IllegalArgumentException("at least 2 paths expected");
        }
        for (int i = 1; i < paths.length; i++) {
            checkCompareIsBefore(createPath(paths[i - 1]), createPath(paths[i]));
        }
    }

    private static void checkCompareIsBefore(MemoryPath first, MemoryPath second) {
        int firstToSecond = first.compareTo(second);
        int secondToFirst = second.compareTo(first);
        if (first.equals(second)) {
            assertThat(firstToSecond).isEqualTo(secondToFirst).isEqualTo(0);
        } else {
            assertThat(firstToSecond).describedAs(first + " must be before " + second).isLessThan(0);
            assertThat(secondToFirst).describedAs(second + " must be after " + first).isGreaterThan(0);
        }
    }

    private static void checkAlreadyNormalized(String p, String expected) {
        assertThat(createPath(p).normalize()).isEqualTo(createPath(expected));
    }

    private static void checkParts(Path p, String... expectedParts) {
        assertThat(p.getNameCount()).isEqualTo(expectedParts.length);

        // check parents through iterable interface
        Path[] expectedPaths = new Path[expectedParts.length];
        int i = 0;
        for (String part : expectedParts) {
            expectedPaths[i++] = createPath(part);
        }
        assertThat(p).containsSequence(expectedPaths);

        // test each of them through getName
        i = 0;
        for (Path part : expectedPaths) {
            assertThat(part).isEqualTo(p.getName(i));
            if (i == 0) {
                // getName(0) return identity;
                assertThat(p.getParent()).isEqualTo(part);
            }
            i++;

            // toUri converts to absolute path, thus we can't test a lot more than suffix
            assertThat(p.toUri().getPath()).startsWith(part.toUri().getPath());

            // if path is absolute, then its parts path must be absolute
            // also, both paths must have the same root
            if (p.isAbsolute()) {
                assertThat(part.isAbsolute()).isTrue();
                assertThat(part.getRoot()).isEqualTo(p.getRoot());
            } else {
                assertThat(part.isAbsolute()).isFalse();
                assertThat(part.getRoot()).isNull();
            }
        }
    }

    private static void checkFileName(Path path, String expectedFileName) {
        Path fileName = path.getFileName();
        if (null == expectedFileName) {
            assertThat(fileName).isNull();
        } else {
            assertThat(fileName.isAbsolute()).describedAs("relative path").isFalse();
            assertThat(fileName).isEqualTo(createPath(expectedFileName));
        }
    }

    private static MemoryPath createAndCheckPath(String path, String expectedPath) {
        MemoryPath item = createPath(path);
        String expectedItemPath = (item.isAbsolute() ? "" : "/") + expectedPath;
        assertThat(item.toUri().getPath()).isEqualTo(expectedItemPath);
        return item;
    }

    private static MemoryPath createPath(String path) {
        MemoryPath result = MemoryPath.create(defaultFs, path);
        URI uri = result.toUri();
        assertThat(uri.getScheme()).isEqualTo("memory");
        return result;
    }

    private static MemoryFileSystem createFs() {
        MemoryFileSystemProvider provider = new MemoryFileSystemProvider();
        return MemoryFileSystem.builder(provider).build();
    }

    private static Path anotherPathType() {
        Path path = Paths.get("/dummy/path/in/default/fs");
        assertThat(path).isNotInstanceOf(MemoryPath.class);
        return path;
    }
}

