import java.io.File;
import java.io.FileOutputStream;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

public class FileBuilder implements Runnable{
  final LocalStorage storage;
  final String fileId;
  final String path;
  final int expectedNrChunks;

  public FileBuilder(LocalStorage storage, String fileId, String path, int expNChunks){
    this.storage = storage;
    this.fileId = fileId;
    this.path = path;
    this.expectedNrChunks = expNChunks;
  }


  @Override
  public void run() {
    CopyOnWriteArrayList<ChunkData> list = storage.getRestoring(fileId);
    if(list != null) {
      if(list.size() == expectedNrChunks) {
        File file = new File(path);

        try(FileOutputStream stream = new FileOutputStream(file)) {
          if(file.exists()) {
            file.delete();
          }
          file.createNewFile();

          list.sort(Comparator.comparingInt(o -> o.nr));

          for(ChunkData chunk : list) {
            byte[] content = chunk.chunk;
            stream.write(content);
          }
        } catch(Exception e) {
          System.out.println("Failed restoring file.");
        }
      } else {
        System.out.println("Not all chunks retrieved for restoring.");
        System.out.println(list.size());
      }
      storage.removeRestoring(fileId);
    } else {
      System.out.println("File not queued to be restored.");
    }
  }

}
