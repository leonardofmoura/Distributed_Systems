
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class that has all the methods that the Peer needs
 *  to utilize the protocols: Backup, Restore, Deletion of files 
 *  in the system, get State of Peer in the System (storage and chord information)
 */
public class PeerMethods implements PeerInterface {
	public static int CHUNK_SIZE = 16000; // Max size of the chunks
	public static double FILE_MAX_SIZE = 1000 * 1000000; // limit of file size for the system to handle

	/**
	 * Divide file into chunks and store them.
	 */
	public void backup(String path, int repDegree) {
		Peer.pool.execute(() -> {
			try {
				chunkifyFile(path, repDegree);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Receive chunks from peers and rebuild file.
	 */
	public void restore(String path) {
		Peer.pool.execute(() -> {
			try {
				restoreFile(path);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Delete file from the system
	 */
	public void delete(String path) {
		Peer.pool.execute(() -> {
			try {
				deleteFile(path);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Receives file path of the File that we want to restore, and checks in Storage for its FileInfo,
	 *  if found, initiates the restore protocol for each Chunk that composes the File. On sucess of all 
	 *  chunks reconstructs the file
	 */
	public void restoreFile(String filePath) {
		FileInfo file = Peer.storage.getFileInfoByFilePath(filePath);
		if (file == null) {
			System.out.println("You can only restore files that have" + "been previously backed up by the system.");
			return;
		}
		String fileId = file.getId();
		int numChunks = file.getChunks().size();

		for (int chunkNo = 1; chunkNo <= numChunks; chunkNo++) {
			try {
				byte[] chunk = restoreChunk(fileId, chunkNo, file.getRepDegree());
				if (chunk != null) {
					Peer.storage.restoreChunk(chunk);
				} else {
					System.out.println("Couldn't restore the chunk number " + chunkNo);
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// reconstructing the file
		dechunkyFile(filePath);
	}

	/**
	 * Receives file path of the File that we want to delete, and checks in Storage for its FileInfo,
	 *  if found, initiates the delete protocol for each Chunk that composes the File. After that 
	 *  deletes the FileInfo from Storage
	 */
	public void deleteFile(String filePath) throws IOException {
		FileInfo file = Peer.storage.getFileInfoByFilePath(filePath);
		if (file == null) {
			System.out.println("You can only delete files that have" + "been previously backed up by the system.");
			return;
		}
		String fileId = file.getId();
		int numChunks = file.getChunks().size();

		for (int chunkNo = 1; chunkNo <= numChunks; chunkNo++) {
			try {
				boolean success = deleteChunk(fileId, chunkNo, file.getRepDegree());
				if (!success) {
					System.out.println("Couldn't delete the chunk number " + chunkNo);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		Peer.storage.removeBackedFile(file);
		System.out.println("All " + filePath + " chunks were deleted");
	}

	/**
	 * Get reference to node with chunk chunkNo from file fileID.
	 */
	static private NodeReference getNode(String fileId, int chunkNo, int i) throws NoSuchAlgorithmException {
		BigInteger chunkChordId = getHash(fileId, chunkNo, i);
		NodeReference receiverNode = Peer.chordNode.findSuccessor(chunkChordId);
		System.out.println(">>> Chunk Hash: " + chunkChordId + " <<<");
		System.out.println(">>> Successor ID: " + receiverNode.id + " <<<");
		return receiverNode;
	}

	/**
	 * Receives fileID, chunk number and the number of copies in the system for the wanted chunk and, for each one of the 
	 *  copies, uses chord to get the Node that should have them and sends a GETCHUNK message, checks reply for errors,
	 *  and on sucess breaks the loop, and returns the chunk content in byte[] form
	 */
	public byte[] restoreChunk(String fileId, int chunkNo, int repDegree) throws IOException, NoSuchAlgorithmException {
		byte[] chunk = null;

		/* For each owner of a copy of the chunk */
		for (int i = 0; i < repDegree; i++) {
			NodeReference receiverNode = getNode(fileId, chunkNo, i);
			if(receiverNode==null) continue;
			byte[] msg = MessageBuilder.getGetchunkMessage(fileId, chunkNo, i);

			try (SSLSocketStream socket = new SSLSocketStream(receiverNode.ip, receiverNode.port)) {		
				/* Send GETCHUNK message */
				socket.write(msg);

				/* Get CHUNK message from peer */
				byte[] fromClient = new byte[65000];
				int msgSize;
				if ((msgSize = socket.read(fromClient)) != -1) {
					ByteArrayOutputStream message = new ByteArrayOutputStream();
					message.write(fromClient, 0, msgSize);
					String headerString = new String(fromClient).split("\\r?\\n")[0];
					if (headerString.equals("ERROR")) {
						System.out.println("Warning: error while restoring chunk");
						continue;
					} else {
						String[] tokens = headerString.split(" ");
						if (tokens[0].equals("PROTOCOL") && tokens[1].equals("CHUNK")) {
							if (tokens[2].equals(fileId) && tokens[3].equals(String.valueOf(chunkNo))) {
								chunk = message.toByteArray();
								break;
							} else {
								System.out.println("Warning: wrong chunk while restoring chunk");
								System.out.println("   : received " + tokens[2] + "_" + tokens[3] + "_" + tokens[4]
										+ " | wanted" + fileId + "_" + chunkNo + "_" + i);
							}
						} else {
							System.out.println("Warning: wrong message while restoring chunk");
							System.out.println("   : received \"" + tokens[0] + " " + tokens[1]
									+ "\" | wanted \"PROTOCOL CHUNK\"");
						}
					}
				}
			} catch (SSLManagerException e) {
				System.out.println("Exception thrown: " + e.getMessage());
			}
		}
		return chunk;
	}

	/**
	 *  Receives fileID, chunk number and the number of copies in the system for the desired chunk and, for each one of the 
	 *   copies, uses chord to get the Node that should have them and sends a DELETE message, checks reply for errors,
	 *   and on sucess of deletion of all copies returns true
	 */
	public boolean deleteChunk(String fileId, int chunkNo, int repDegree) throws IOException, NoSuchAlgorithmException {

		for (int i = 0; i < repDegree; i++) {
			NodeReference receiverNode = getNode(fileId, chunkNo, i);
			if(receiverNode==null) continue;

			byte[] msg = MessageBuilder.getDeleteMessage(fileId, chunkNo);

			try (SSLSocketStream socket = new SSLSocketStream(receiverNode.ip, receiverNode.port)) {
				/* Send DELETE message */
				socket.write(msg);

				byte[] fromClient = new byte[65000];
				int msgSize;
				if ((msgSize = socket.read(fromClient)) != -1) {
					ByteArrayOutputStream message = new ByteArrayOutputStream();
					message.write(fromClient, 0, msgSize);
					if (new String(fromClient).equals("ERROR")) {
						continue;
					} else if (new String(fromClient).equals("SUCCESS")) {
						System.out.println("NICE!");
						continue;
					}
				}
			} catch (SSLManagerException e) {
				System.out.println("Exception thrown: " + e.getMessage());
			}
		}
		return true;
	}

	/**
	 *  Receives file path and desired number of copies and if file is found and is not too big,
	 *  creates FileInfo and puts it  in Storage, breaks file in chunks, and for each of these chunks
	 *  creates a ChunkInfo that is added to the FileInfo
	 */
	public void chunkifyFile(String filePath, int repDegree) throws IOException, NoSuchAlgorithmException {
		// file handling
		InputStream is;
		try {
			is = new FileInputStream(filePath);
		} catch (FileNotFoundException e) {
			System.out.println("File not found");
			return;
		}

		Path path = Paths.get(filePath);
		if (path.toFile().length() > FILE_MAX_SIZE) {
			System.out.println("File too big, max size: 1GBytes");
			is.close();
			return;
		}
		FileInfo newFile = new FileInfo(filePath, repDegree);
		Peer.storage.addBackedFile(newFile);

		int chunkno = 1;
		int chunkSize = 0;
		byte[] b = new byte[CHUNK_SIZE];

		while ((chunkSize = is.read(b)) != -1) {
			ByteArrayOutputStream body = new ByteArrayOutputStream();
			body.write(b, 0, chunkSize);
			byte[] chunkBody = body.toByteArray();
			newFile.addChunk(new ChunkInfo(chunkno, newFile.getId(), repDegree, chunkBody.length));
			backupChunk(newFile.getId(), chunkno, repDegree, chunkBody, newFile);
			chunkno++;
		}
		is.close();
	}

	/**
	 * Restores file from all the temporary chunks in the temp folder, and puts the
	 *  restored file in a restored folder, on sucess deletes all the temporary chunks
	 */
	public void dechunkyFile(String filePath) {
		FileInfo file = Peer.storage.getFileInfoByFilePath(filePath);
		String fileId = file.getId();
		int numChunks = file.getChunks().size();

		String restoredDirPath = "Peers/dir" + Integer.toString(Peer.id) + "/restored";
		File restoredPath = new File(restoredDirPath);
		restoredPath.mkdir();

		File restoredFile = new File(restoredDirPath + "/" + filePath.split("/")[filePath.split("/").length - 1]);
		try {
			restoredFile.createNewFile();
			OutputStream os = new FileOutputStream(restoredFile);
			for (int i = 1; i <= numChunks; i++) {
				File chunk = new File(
						"Peers/dir" + Integer.toString(Peer.id) + "/temp" + "/" + fileId + "/" + fileId + "_" + i);
				os.write(Files.readAllBytes(chunk.toPath()));
				chunk.delete();
				new File("Peers/dir" + Integer.toString(Peer.id) + "/temp" + "/" + fileId).delete();
			}
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Receives fileID, chunk number ,the number of wanted copies in the system for the chunk to backup, and, for each one of the 
	 *  copies, uses chord to get the Node where it should be stored and sends a PUTCHUNK message, checks reply for errors,
	 *  and on sucess increments the current number of copies in the system
	 */
	public void backupChunk(String fileId, int chunkNo, int repDegree, byte[] body, FileInfo file)
			throws NoSuchAlgorithmException, IOException {
		for (int i = 0; i < repDegree; i++) {
			NodeReference receiverNode = getNode(fileId, chunkNo, i);
			if(receiverNode==null) { i--; continue; }

			byte[] msg = MessageBuilder.getPutchunkMessage(fileId, chunkNo, body, i);
			try (SSLSocketStream socket = new SSLSocketStream(receiverNode.ip, receiverNode.port)) {
				socket.write(msg);

				String fromServer;
				if ((fromServer = socket.readLine()) != null) {
					if (fromServer.equals("SUCCESS")) {
						// If Node receives a sucess as answer we increment the chunk current
						// number of copies on the System
						file.getChunkByNo(chunkNo).incrementCurrRepDegree();
					}
					if (fromServer.equals("ERROR")) {
						// If error message is received it means the system couldn't store the file in the system
						System.out.print("ERROR: Peer couldn't store chunk.");
					}
				} else {
					System.out.println("ERROR: Backup answer was empty.");
				}
			} catch (SSLManagerException e) {
				System.out.println("Exception thrown: " + e.getMessage());
			}
		}
	}

	static public boolean backupChunk(String fileId, int chunkNo, int copyNo, byte[] body) throws NoSuchAlgorithmException, IOException {
		NodeReference receiverNode = getNode(fileId, chunkNo, copyNo);
		if(receiverNode == null) return false;

		byte[] msg = MessageBuilder.getPutchunkMessage(fileId, chunkNo, body, copyNo);
		try (SSLSocketStream socket = new SSLSocketStream(receiverNode.ip, receiverNode.port)) {
			socket.write(msg);

			String fromServer;
			if ((fromServer = socket.readLine()) != null) {
				if (fromServer.equals("SUCCESS")) {

					return true;
				}
				if (fromServer.equals("ERROR")) {
					// If error message is received it means the system couldn't store the file in the system
					System.out.print("ERROR: Peer couldn't store chunk.");
					return false;
				}
			} else {
				System.out.println("ERROR: Backup answer was empty.");
				return false;
			}
		} catch (SSLManagerException e) {
			System.out.println("Exception thrown: " + e.getMessage());
			return false;
		}
		return false;
	}

	/**
	 * Receives the PUTCHUNK message that was inteed for himself and builts a DELEGATE 
	 *  message that it sends to its sucessor, on sucess updates the ChunkInfo on Storage
	 *  to hold the information that the chunk was delegated and who stored it, and returns true,
	 *  on error returns false
	 */
	public static boolean delegateChunk(byte[] chunk) {
		NodeReference successor = Peer.chordNode.successor;

		// BREAK chunk into message
		String chunkTxt = new String(chunk);
		String[] chunkpieces = chunkTxt.split("\\s+|\n");
		String fileId = chunkpieces[2];
		int chunkNo = Integer.parseInt(chunkpieces[3]);
		int copyNo = Integer.parseInt(chunkpieces[4]);
    
		byte[] body = Storage.split(chunk).get(1);
		byte[] msg = null;
		try {
			msg = MessageBuilder.getDelegateMessage(fileId, chunkNo, copyNo, body);
		} catch (IOException e) {
			System.out.println("Exception thrown: " + e.getMessage());
		}
    	
		try (SSLSocketStream socket = new SSLSocketStream(successor.ip, successor.port)) {
			socket.write(msg);

			String fromServer;
			if ((fromServer = socket.readLine()) != null) {

			
			if (fromServer.equals("SUCCESS")) {
				// If Node receives a sucess we add in our storage that we delegated that chunk
				ChunkInfo delegatedChunkInfo = new ChunkInfo(chunkNo, fileId, 0, body.length);
				delegatedChunkInfo.delegate(successor);
				Peer.storage.addStoredChunk(delegatedChunkInfo);
				return true;
				
			}
			if (fromServer.equals("ERROR")) {
				System.out.print("ERROR: Peer couldn't store chunk.");
				return false;
			}
			} else {
			System.out.println("ERROR: Backup answer was empty.");
			}
		} catch (SSLManagerException e) {
			System.out.println("Exception thrown: " + e.getMessage());
		}
		
		return false;
  }

  /**
	 * Receives the chunk contents, the fileID, the Chunkno and Copy number that was inteed for himself and builts a DELEGATE 
	 *  message that it sends to its sucessor, on sucess updates the ChunkInfo on Storage
	 *  to hold the information that the chunk was delegated and who stored it, and returns true,
	 *  on error returns false
	 */
	public static boolean delegateChunk(byte[] chunk, String fileId, int chunkNo, int copyNo) {
		NodeReference successor = Peer.chordNode.successor;

		byte[] body = chunk;
		byte[] msg = null;
		try {
			msg = MessageBuilder.getDelegateMessage(fileId, chunkNo, copyNo, body);
		} catch (IOException e) {
			System.out.println("Exception thrown: " + e.getMessage());
		}
    	
		try (SSLSocketStream socket = new SSLSocketStream(successor.ip, successor.port)) {
			socket.write(msg);

			String fromServer;
			if ((fromServer = socket.readLine()) != null) {

			
			if (fromServer.equals("SUCCESS")) {
				// If Node receives a sucess we add in our storage that we delegated that chunk
				ChunkInfo delegatedChunkInfo = new ChunkInfo(chunkNo, fileId, 0, body.length);
				delegatedChunkInfo.delegate(successor);
				Peer.storage.addStoredChunk(delegatedChunkInfo);
				return true;
				
			}
			if (fromServer.equals("ERROR")) {
				System.out.print("ERROR: Peer couldn't store chunk.");
				return false;
			}
			} else {
			System.out.println("ERROR: Backup answer was empty.");
			}
		} catch (SSLManagerException e) {
			System.out.println("Exception thrown: " + e.getMessage());
		}
		
		return false;
  }

  /**
   * Receives a chunk content and checks if there is enough space available to fit it, if yes 
   *  calls the Storage method saveFile and returns true, if there isn't enough space, prints a message
   *  and returns false
   */
  public static boolean saveChunk(byte[] chunk) {
	System.out.println("CURRENT MAX STORAGE: " + Peer.storage.getMaxStorage());
	System.out.println("CURRENT STORAGE: " + Peer.storage.getCurrStorage());
    long diff = (Peer.storage.getCurrStorage() + chunk.length) - Peer.storage.getMaxStorage();
    if (diff > 0 && Peer.storage.getMaxStorage() != -1) {
      System.out.println("No Space Available");
      return false;
    }
    Peer.storage.saveFile(chunk);
    return true;
  }

  /**
   * Receives the information about the chunk that it needs to send, and checks its storage for it,
   *  checks if chunk was delegated, if not retrieves the chunk from storage and returns it as byte[],
   *  if it was delegated, gets the Node that stored from the Storage and sends it a GETCHUNK message,
   *  receives the chunk contents as answer and returns it as byte[]
   */
  public static byte[] retrieveChunk(String fileId, int chunkNo, int copyNo) throws IOException {
    byte[] chunk = null;
	String key = fileId + "_" + chunkNo + "_" + copyNo;
	ChunkInfo chunkInfo = Peer.storage.getStoredChunkInfo(fileId, chunkNo);
	byte[] msg = MessageBuilder.getGetchunkMessage(fileId, chunkNo, copyNo);

	if(chunkInfo.getDelegated()) {
		NodeReference node = chunkInfo.getReceiver();
		try (SSLSocketStream socket = new SSLSocketStream(node.ip, node.port)) {
			socket.write(msg);

			byte[] fromClient = new byte[65000];
			int msgSize;
			if ((msgSize = socket.read(fromClient)) != -1) {
				ByteArrayOutputStream message = new ByteArrayOutputStream();
				message.write(fromClient, 0, msgSize);
				if (new String(fromClient).equals("ERROR")) {
				} else {
					chunk = fromClient;
				}
			}
		} catch (SSLManagerException e) {
			System.out.println("Exception thrown: " + e.getMessage());
		}
	}
	else {
		Path file = Paths.get("Peers/dir" + Peer.id + "/" + key);
		byte[] fileData = Files.readAllBytes(file);
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(fileData);
		chunk = MessageBuilder.getChunkMessage(fileId, chunkNo, copyNo, body.toByteArray());
	}

    return chunk;
  }

  /**
   * Receives the information about the chunk that it needs to delete, and checks its storage for it,
   *  checks if chunk was delegated, if not deletes the chunk from storage and from the system,
   *  if it was delegated, gets the Node that stored from the Storage and sends it a DELETE message,
   *  returns true if successfully deleted, and false if otherwise
   */
  public static boolean deleteSavedChunk(String fileId, int chunkNo) throws IOException{
		String key = fileId + "_" + chunkNo;
		ChunkInfo chunk = Peer.storage.getStoredChunkInfo(fileId, chunkNo);
		if (chunk != null) {
			if(chunk.getDelegated()){
				NodeReference node = chunk.getReceiver();
			try (SSLSocketStream socket = new SSLSocketStream(node.ip, node.port)) {
				byte[] msg = MessageBuilder.getDeleteMessage(fileId, chunkNo);
				socket.write(msg);

				byte[] fromClient = new byte[65000];
				int msgSize;
				if ((msgSize = socket.read(fromClient)) != -1) {
					ByteArrayOutputStream message = new ByteArrayOutputStream();
					message.write(fromClient, 0, msgSize);
					if (new String(fromClient).equals("ERROR")) {
						return false;
					} else if (new String(fromClient).equals("SUCCESS")) {
						return true;
					}
				}
			} catch (SSLManagerException e) {
				System.out.println("Exception thrown: " + e.getMessage());
			}
				} else {
					Path file = Paths.get("Peers/dir" + Peer.id + "/" + key);
					if (!file.toFile().delete()) {
						return false;
					}
					Peer.storage.removeStoredChunk(chunk);
					return true;
				}
			}
		return false;
  }

  /**
   * Sets max storage for storing files , and if new maximum is over the current ocuppied storage
   *  calls manageStorage method that will solve that issue
   */
  public void spaceReclaim(long newMaxStorage) throws IOException {
    newMaxStorage *= 1000;
    if (newMaxStorage < 0) {
      Peer.storage.setMaxStorage(-1);
    } else {
      long spaceToFree = Peer.storage.getCurrStorage() - newMaxStorage;
      Peer.storage.setMaxStorage(newMaxStorage);
      if (spaceToFree > 0) {
        manageStorage(spaceToFree, true);
      }
    }
    return;
  }

  /**
   * Receives the needed bytes to free from storage and checks storage for saved chunks to delete,
   *  on deletion of a chunk, removes it from local system and delegates it to its sucessor,
   *  on enough deletions returns true
   */
  public static boolean manageStorage(long spaceToFree, boolean mustDelete) throws IOException {
    // If Max Storage is -1 it means it is unlimited
    if (Peer.storage.getMaxStorage() == -1) {
      return true;
    }
    int maxRepdegreeDif;
    long freedSpace = 0;
    ChunkInfo toRemove;
    while (freedSpace < spaceToFree) {
      maxRepdegreeDif = -10;
      toRemove = null;
      for (ChunkInfo chunk : Peer.storage.getChunksStored()) {
        int repDegreeDif = chunk.getCurrRepDegree() - chunk.getWantedRepDegree();
        if (repDegreeDif > maxRepdegreeDif) {
          maxRepdegreeDif = repDegreeDif;
          toRemove = chunk;
        }
      }
      if (mustDelete) {
        File chunkFile = new File("Peers/" + "dir" + Peer.id + "/" + toRemove.getChunkID() + "_" + toRemove.getCopyNo());
        Peer.storage.removeFromCurrStorage(chunkFile.length());
		freedSpace += chunkFile.length();
		byte[] fileData = Files.readAllBytes(chunkFile.toPath());
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(fileData);
		delegateChunk(body.toByteArray(), toRemove.getFileID(), toRemove.getNo(), toRemove.getCopyNo());
        chunkFile.delete();
      } else if (maxRepdegreeDif > 0) {
        File chunkFile = new File("Peers/" + "dir" + Peer.id + "/" + toRemove.getChunkID() + "_" + toRemove.getCopyNo());
        Peer.storage.removeFromCurrStorage(chunkFile.length());
        freedSpace += chunkFile.length();
        freedSpace += chunkFile.length();
		byte[] fileData = Files.readAllBytes(chunkFile.toPath());
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(fileData);
		delegateChunk(body.toByteArray(), toRemove.getFileID(), toRemove.getNo(), toRemove.getCopyNo());
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Given an entry of a new Node into the Chord Ring, this method checks the storage to see if there are any
   * chunks that belong to the newly added Node. In other words, it checks if the chunk ID isnt between the
   * new predecessor and the node. If it isn't, it means the chunk actually belongs to the new predecessor
   */
  static public void giveChunks(NodeReference n) throws NoSuchAlgorithmException { 
	List<ChunkInfo> toRemove = new ArrayList<>();
	List<File> toDelete = new ArrayList<>();
	for (int i = 0;  i < Peer.storage.getChunksStored().size(); i++) {
		ChunkInfo chunk = Peer.storage.getChunksStored().get(i);
		BigInteger chunkHash = getHash(chunk.getFileID(), chunk.getNo(), chunk.getCopyNo());

		if(!Peer.chordNode.clockwiseInclusiveBetween(chunkHash, n.id, Peer.chordNode.id)) {
			// backup protocol no NodeReference n
			try {
				File chunkFile = new File("Peers/" + "dir" + Peer.id + "/" + chunk.getChunkID() + "_" + chunk.getCopyNo());
				Peer.storage.removeFromCurrStorage(chunkFile.length());
				byte[] fileData = Files.readAllBytes(chunkFile.toPath());
				ByteArrayOutputStream body = new ByteArrayOutputStream();
				body.write(fileData);
				if(backupChunk(chunk.getFileID(), chunk.getNo(), chunk.getCopyNo(), body.toByteArray())){
					toRemove.add(chunk);
					toDelete.add(chunkFile);
				}
					
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	  }
	  Peer.storage.getChunksStored().removeAll(toRemove);
	  for (File file : toDelete) {
		  file.delete();
	  }
	  Peer.pool.schedule(() -> {Peer.givingChunks = false;}, 2, TimeUnit.SECONDS);
	}

  /**
   * Print storage state and Chord state when requested
   */
  public void printState() {
    System.out.println("Files Backed Up:");
    for (FileInfo file : Peer.storage.getFilesBacked()) {
      System.out.println(file.toString());
    }
    System.out.println("\nChunks Stored:\n-");
    for (ChunkInfo chunkInfo : Peer.storage.getChunksStored()) {
      System.out.println(chunkInfo.toString());
      System.out.println('-');
    }
    if (Peer.storage.getMaxStorage() == 0) {
      System.out.println("Storage Capacity: Unlimited");
    } else {
      System.out.println("Storage Capacity: " + (Peer.storage.getMaxStorage() / 1000) + " KBytes");
    }
	System.out.println("Storage Used: " + (Peer.storage.getCurrStorage() / 1000) + " KBytes");
	System.out.println("Chord ID: " + Peer.chordNode.id);
	if(Peer.chordNode.successor==null) System.out.println("Chord Successor: Null");
	else System.out.println("Chord Successor: " + Peer.chordNode.successor.id);
	if(Peer.chordNode.predecessor==null) System.out.println("Chord Predecessor: Null");
	else System.out.println("Chord Predecessor: " + Peer.chordNode.predecessor.id);
    return;
  }

  /**
   * Receives the File ID, chunk number and copy number to build a Hash using SHA-1, truncates it,
   *  and returns it for Chord use
   */
  static private BigInteger getHash(String fileId, int chunkNo, int copyNo)
      throws NoSuchAlgorithmException {
    String unhashedId = fileId + "_" + chunkNo + "_" + copyNo;
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    byte[] messageDigest = md.digest(unhashedId.getBytes());
    BigInteger toNum = new BigInteger(1, messageDigest);
    while (toNum.compareTo(new BigInteger("1000000000")) == 1) {
      toNum = toNum.divide(new BigInteger("10"));
    }
    return toNum;
  }
}
