package io.avaje.config;


import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class FileWatchTest {

  @Test
  public void test_when_notChanged() {

    CoreConfiguration config = newConfig();
    List<File> files = files();
    final FileWatch watch = new FileWatch(config, files, true);

    assertThat(config.size()).isEqualTo(2);
    // not touched
    watch.check();
    // no reload
    assertThat(config.size()).isEqualTo(2);
  }

  @Test
  public void test_check_whenTouched_expect_loaded() throws InterruptedException {

    CoreConfiguration config = newConfig();
    List<File> files = files();
    final FileWatch watch = new FileWatch(config, files, true);

    assertThat(config.size()).isEqualTo(2);
    assertThat(config.get("one", null)).isNull();

    touchFiles(files);
    // check after touch means files loaded
    watch.check();

    // properties loaded as expected
    final int size0 = config.size();
    assertThat(size0).isGreaterThan(2);
    assertThat(config.get("one", null)).isEqualTo("a");
    assertThat(config.getInt("my.size", 42)).isEqualTo(17);
    assertThat(config.getBool("c.active", false)).isTrue();
  }

  @Test
  public void test_check_whenTouchedScheduled_expect_loaded() throws InterruptedException {

    CoreConfiguration config = newConfig();
    List<File> files = files();
    final FileWatch watch = new FileWatch(config, files, true);
    System.out.println(watch);

    // touch but scheduled check not run yet
    touchFiles(files);

    // assert not loaded
    assertThat(config.size()).isEqualTo(2);

    // wait until scheduled check has been run
    Thread.sleep(3000);

    // properties loaded as expected
    assertThat(config.size()).isGreaterThan(2);
    assertThat(config.get("one", null)).isEqualTo("a");
    assertThat(config.getInt("my.size", 42)).isEqualTo(17);
    assertThat(config.getBool("c.active", false)).isTrue();
  }

  @Test
  public void test_check_whenFileWritten() throws Exception {

    CoreConfiguration config = newConfig();
    List<File> files = files();

    final FileWatch watch = new FileWatch(config, files, true);
    touchFiles(files);
    watch.check();

    // properties loaded as expected
    final int size0 = config.size();
    assertThat(size0).isGreaterThan(2);
    assertThat(config.get("one", null)).isEqualTo("a");

    writeContent("one=NotA");

    watch.check();
    assertThat(config.get("one", null)).isEqualTo("NotA");

    writeContent("one=a");
    watch.check();
    assertThat(config.get("one", null)).isEqualTo("a");
  }

  private void writeContent(String content) throws IOException {
    File aProps = new File("./src/test/resources/watch/a.properties");
    FileWriter fw = new FileWriter(aProps);
    fw.write(content);
    fw.close();
  }

  private CoreConfiguration newConfig() {
    final Properties properties = new Properties();
    properties.setProperty("config.watch.delay", "1");
    properties.setProperty("config.watch.period", "1");
    return new CoreConfiguration(properties);
  }

  private List<File> files() {
    List<File> files = new ArrayList<>();
    files.add(new File("./src/test/resources/watch/a.properties"));
    files.add(new File("./src/test/resources/watch/b.yaml"));
    files.add(new File("./src/test/resources/watch/c.yml"));
    return files;
  }

  private void touchFiles(List<File> files) throws InterruptedException {
    Thread.sleep(10);
    for (File file : files) {
      file.setLastModified(System.currentTimeMillis());
    }
  }
}