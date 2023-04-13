import java.util.concurrent.*;

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
 * What went wrong?: Double removal? two servants could have tried to remove same present
 * Solution? the 2 lock thing? checking if present within chain before and after acquiring locks?
 *      Look at Chapter 9
 */

public class ProblemOne {
    public static void main(String[] args) {

    }
}
