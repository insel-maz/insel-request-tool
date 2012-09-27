package de.inseltroll.inselrequesttool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 *
 * @author maz
 */
public class Program {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws IOException {
		// http://www.tutego.de/blog/javainsel/2011/06/java-7-filesystem-und-path/
		byte[] requestBytes = Files.readAllBytes(Paths.get("request.txt"));

		System.out.println("Request:");
		// http://docs.oracle.com/javase/7/docs/technotes/guides/intl/encoding.doc.html
		System.out.println(new String(requestBytes, "ISO-8859-1")); // windows-1252

		try (/*OutputStream outputStream = Files.newOutputStream(Paths.get("response.txt"));*/
				SeekableByteChannel outputByteChannel = Files.newByteChannel(Paths.get("response.txt"),
						StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				Selector selector = Selector.open();
				SocketChannel socketChannel = SocketChannel.open()) {
			socketChannel.configureBlocking(false);
			SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_CONNECT);
			socketChannel.connect(new InetSocketAddress("google.de", 80)); // wirft, wenn hostname nicht aufgelöst werden kann

			ByteBuffer requestByteBuffer = ByteBuffer.wrap(requestBytes);
			ByteBuffer responseByteBuffer = ByteBuffer.allocate(12000);

			for (;;) {
				selector.select();
				if (selector.selectedKeys().isEmpty()) {
					continue;
				}
				selector.selectedKeys().clear();

				if (selectionKey.isConnectable()) {
					socketChannel.finishConnect();
					System.out.println("Connected.");
				}
				if (selectionKey.isReadable()) {
					int numBytesRead;
					try {
						numBytesRead = socketChannel.read(responseByteBuffer);
					} catch (IOException ex) {
						System.out.println("Read error: " + ex.getMessage());
						break;
					}
					if (numBytesRead == -1) {
						System.out.println("Close...");
						break;
					}
					System.out.println("Read " + numBytesRead);

					responseByteBuffer.flip();
					while (responseByteBuffer.hasRemaining()) {
						outputByteChannel.write(responseByteBuffer);
					}
					responseByteBuffer.clear();
				}
				if (selectionKey.isWritable()) {
					// TODO: try-catch
					int numBytesWritten = socketChannel.write(requestByteBuffer);
					System.out.println("Write " + numBytesWritten);
					if (!requestByteBuffer.hasRemaining()) {
						System.out.println("Shutdown output...");
						socketChannel.shutdownOutput();
					}
				}

				int interestOps = 0;
				if (socketChannel.isConnectionPending()) {
					interestOps |= SelectionKey.OP_CONNECT;
				} else {
					interestOps |= SelectionKey.OP_READ;
					if (requestByteBuffer.hasRemaining()) {
						interestOps |= SelectionKey.OP_WRITE;
					}
				}
				selectionKey.interestOps(interestOps);
			}
		}

	}
}
