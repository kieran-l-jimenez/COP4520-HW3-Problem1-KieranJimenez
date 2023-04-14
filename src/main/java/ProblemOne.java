import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.stream.IntStream;

/* Problem 1: The Birthday Presents Party
 * Presents - Randomly ordered and unique IDs, [500,000] presents
 * Present Chain - Linked List storage of presents in increasing order
 * 4 servants - 4 threads
 * Each servant does one of three actions without order (randomly select action)
 *  1. Hook present from bag into chain (Insertion, hook present to predecessor and link to successor)
 *  2. Write "Thank You" and remove present (Deletion, unlink present from predecessor and connect predecessor to successor)
 *  3. Lookup if present is within chain (Scan without Insertion or Deletion)
 * Every servant alternated between 1 and 2
 * PROBLEM: More presents than thank you notes!
 * What went wrong?: Double removal? two servants could have tried to remove same present but they still think they removed two
 * Solution? the 2 lock thing? checking if present within chain before and after acquiring locks?
 *  Implemented Non-Blocking Synchronization
 */

public class ProblemOne {
    static AtomicInteger atomCounter = new AtomicInteger();//used to pick random presents
    static int[] presentBag;

    public static void main(String[] args) throws InterruptedException {
        //generates and orders 500,000 unique present IDs
        presentBag = IntStream.rangeClosed(1, 500000).toArray();
        Collections.shuffle(Collections.singletonList(presentBag));

        //creates 4 servants
        servantThread[] staff = new servantThread[4];
        for (int i = 0; i < 4; i++) {
            staff[i] = new servantThread();
        }

        //get servants to start working
        for (ProblemOne.servantThread servantThread : staff) {
            servantThread.start();
        }
        //wait until every servant is done
        for (ProblemOne.servantThread servantThread : staff) {
            servantThread.join();
        }
        //print every node to show that no more presents are left chained
        //Alright, if everything is successful we should only see the Ur-Fruitcake gifts at either end of the chain!
        Present pred = presentChain.head;
        Present curr = presentChain.head.next.getReference();
        System.out.println("First present in the chain is ID ["+pred.id+"]");
        while (curr != null) {
            System.out.println("Next present in the chain is ID ["+curr.id+"]");
            pred = curr;
            curr = curr.next.getReference();
        }
    }

    public static class servantThread extends Thread {
        public void run() {
            int i = atomCounter.getAndIncrement();
            while (i < 500000) {
                //Every servant asked to alternate adding gifts and writing "Thank You"s
                presentChain.add(presentBag[i]);// add gift
                presentChain.remove(presentBag[i]);// Write Thank You
                i = atomCounter.getAndIncrement();// Grab the next gift in the unsorted pile
            }
        }
    }

    public static class Window {//Represents either node to be removed and predecessor or nodes on either side of where new one should go
        public Present pred;
        public Present curr;

        Window(Present myPred, Present myCurr) {
            pred = myPred;
            curr = myCurr;
        }
    }

    public static Window find(Present head, int key) {
        //Look through the present chain and set aside any presents we've already written "Thank You" for
        Present pred = null;
        Present curr = null;
        Present succ = null;

        boolean[] marked = {false};
        boolean snip;

        retry: while (true) {
            pred = head;//Let's start from the top
            curr = pred.next.getReference();

            while (true) {
                succ = curr.next.get(marked);//sets successor node and sees if curr is logically removed already

                while (marked[0]) {//node is logically removed
                    snip = pred.next.compareAndSet(curr, succ, false, false);// physically remove
                    if (!snip) {//if either of the two values were changed before
                        continue retry;
                    }
                    curr = succ;
                    succ = curr.next.get(marked);
                }

                if (curr.id >= key) {//if curr is or is after the key, return node pair
                    return new Window(pred, curr);
                }
                //no good, move forward
                pred = curr;
                curr = succ;
            }
        }
    }

    public static class Present {
        int id;//present tag
        AtomicMarkableReference<Present> next;//contains reference to next node AND whether node is marked

        public Present(int guestNumber) {
            id = guestNumber;
            next = null;
        }
    }

    public static class presentChain {// the ~Chain of Presents~
        static Present head;
        static Present tail;

        public presentChain() {//use some dreadful Ur-Fruitcakes to hold the ends of the chain
            head = new Present(Integer.MIN_VALUE);
            tail = new Present(Integer.MAX_VALUE);
            head.next = new AtomicMarkableReference(tail, false);
        }

        public static boolean add(int target) {// We have a present from the bag, lets add it to the chain
            int key = target;

            while (true) {
                Window window = find(head, key);//starts at the head of the present chain and find the right location
                Present pred = window.pred;//get pred and curr nodes to use
                Present curr = window.curr;

                if (curr.id == key) { //present somehow already here... Huh?
                    System.out.println("RED ALERT: WE'VE BROKEN THE LAWS OF PHYSICS AND HAVE TWO OF THE SAME THING!");
                    return false;
                } else { //Phew, we only have one copy of this present
                    Present node = new Present(target);//make node, present is in chain mode
                    node.next = new AtomicMarkableReference(curr, false);//new node points to current node
                    //present is chained to the next in line

                    if (pred.next.compareAndSet(curr, node, false, false)) {
                        //previous present is chained to the new one
                        return true;//successfully atomically set the pred to point to the new node
                    }
                }
            }
        }

        public static boolean remove(int target) {
            int key = target;
            boolean snip;

            while (true) {
                Window window = find(head, key);
                Present pred = window.pred;//get pred and curr nodes to use
                Present curr = window.curr;

                if (curr.id != key) {//Nope! Present isn't here!
                    System.out.println("RED ALERT: ONE OF THE SERVANTS IS A THIEF, THE GIFT I JUST ADDED IS MISSING!");
                    return false;
                } else {
                    Present succ = curr.next.getReference();
                    snip = curr.next.attemptMark(succ, true);//logically remove node
                    //"Hey other servants, I will write thank you note for this one!"

                    if (!snip) {//huh, someone already said they've got this present handled...
                        continue;
                    }

                    pred.next.compareAndSet(curr, succ, false, false);
                    //"Dearest guest 'curr.id', thank you for the wonderful gift!"
                    //Also, don't worry as if this fails it'll be removed next time a servant is looking along the chain
                    return true;
                }
            }
        }

        public static boolean contains(int target) {
            boolean[] marked = {false};
            int key = target;
            Present curr = head;

            while (curr.id < key) {
                curr = curr.next.getReference();
                Present succ = curr.next.get(marked);
            }

            return (curr.id == key && !marked[0]);//Found it! No one has written "Thank You" for it yet.
        }
    }
}