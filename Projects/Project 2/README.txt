SDIS: PROJECT 2 - Distributed Backup Service for The Internet 

-Installing

    Compile all the source files:

        javac *.java

-Running

    - Peer

    --> First Peer

    The First Peer to enter the ChordRing must create it

        Peer <peer_id> <ip_Adress> <port_Number> CREATE
            E.g.: java Peer 1 localhost 8888 create

    --> Rest of Peers

    To all the other Peers you must provide and IpAdress and Port of a Peer that already is on the ChordRing
        
        Peer <peer_id> <ip_Adress> <port_Number> JOIN  <chord_Member_Ip_Address> <Chord_Member_Port_Number>
            E.g.: java Peer 2 localhost 8000 join localhost 8888

    - Client Interface

    The Client Interface provides the services availables to the Peers:

        - Backup Protocol: Insert the file path of your file and your desired number of copies to enter into the System
        - Restore Protocol: Insert the file path of the file that you want to restore from the system (The file must be previously put into the System by the user)
        - Delete Protocol: Insert the file path of the file that you want to delete from the system (The file must be previously put into the System by the user)
        - Reclaim Protocol: Insert the max amount of memory you want to provide to the System (KBytes)
        - State : Get information of the files you put into the System, the files you are storing for other Peers and your Node information on the ChordRing

    --> Backup Protocol

    ClientInterface <peer_id> BACKUP <file_path> <desired_replication_degree>
        E.g.:  ClientInterface 1 BACKUP test/souto.html 2

    --> Restore Protocol

    ClientInterface <peer_id> RESTORE <file_path>
        E.g.: ClientInterface 1 RESTORE test/souto.html

    --> Delete Protocol

    ClientInterface <peer_id> DELETE <file_path>
        E.g.:  ClientInterface 1 DELETE test/souto.html

    --> Reclaim Protocol

   ClientInterface <peer_id> BACKUP <max_amount_disk_space>
        E.g.:  ClientInterface 1 RECLAIM 65000

    --> State

    ClientInterface <peer_idt> STATE
        E.g.:  ClientInterface 1 STATE
