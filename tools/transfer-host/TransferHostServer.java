import com.voxivoid.recipelab.transfer.LinkServer;
import com.voxivoid.recipelab.transfer.Logger;
import com.voxivoid.recipelab.transfer.Protocol;
import com.voxivoid.recipelab.transfer.Stats;
import com.voxivoid.recipelab.transfer.StorageRoot;
import java.io.File;

/**
 * Transfer to phone's HTTP server (protocol 2) on a desktop, serving a folder laid out like a memory card, as an
 * ILCE-6000. For testing Recipe Lab Studio without a camera; started by tools/transfer-host-server.sh.
 *
 *   java TransferHostServer <card folder> <port>      port 0 picks a free one; the line "PORT n" says which
 */
public class TransferHostServer {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: TransferHostServer <card folder> <port>");
            System.exit(2);
        }
        String model = "ILCE-6000";
        LinkServer s = new LinkServer(new StorageRoot(new File(args[0])), model, Protocol.friendlyName(model), new Stats(), new Logger() {
            public void info(String m) { System.out.println(m); }
            public void error(String m, Throwable t) { System.out.println(m + " " + t); }
        });
        System.out.println("PORT " + s.start(Integer.parseInt(args[1])));
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}
