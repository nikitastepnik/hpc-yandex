import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.*;
import java.util.stream.Collectors;

public class SetImpl<T extends Comparable<T>> implements Set<T> {

    private static class Window<T> {
        final Node<T> first;
        final Node<T> second;

        public Window(Node<T> first, Node<T> second) {
            this.first = first;
            this.second = second;
        }
    }

    private static class Node<T> {
        final T value;
        final AtomicMarkableReference<Node<T>> next;

        Node(T value, Node<T> next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }
    }

    private final Node<T> head = new Node<>(null, null);

    private Window<T> find(T value) {
        Node<T> prev = null, curr = null, next = null;
        retry:
        while (true) {
            prev = head;
            curr = head.next.getReference();
            while (curr != null) {
                next = curr.next.getReference();
                if (curr.next.isMarked()) {
                    if (!prev.next.compareAndSet(curr, next, false, false)) {
                        continue retry;
                    }
                } else {
                    if (curr.value.compareTo(value) >= 0) {
                        return new Window<T>(prev, curr);
                    }
                    prev = curr;
                }
                curr = next;
            }
            return new Window<T>(prev, curr);
        }
    }

    @Override
    public boolean add(T item) {
        while (true) {
            Window<T> t = find(item);
            Node<T> pred = t.first, curr = t.second;

            if (curr != null && curr.value.equals(item)) {
                return false;
            } else {
                Node<T> next = new Node<>(item, curr);
                if (pred.next.compareAndSet(curr, next, false, false)) {
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T item) {
        while (true) {
            Window<T> t = find(item);
            Node<T> pred = t.first, curr = t.second;
            if (curr == null) {
                return false;
            }
            if (curr.value.compareTo(item) != 0) {
                return false;
            }
            Node<T> tail = curr.next.getReference();
            if (!curr.next.compareAndSet(tail, tail, false, true)) {
                continue;
            }
            pred.next.compareAndSet(curr, tail, false, false);
            break;
        }

        return true;
    }

    @Override
    public boolean contains(T item) {
        Node<T> curr = this.head.next.getReference();
        while (curr != null && curr.value.compareTo(item) < 0) {
            curr = curr.next.getReference();
        }
        if (curr == null) {
            return false;
        }
        return curr.value.compareTo(item) == 0 && !curr.next.isMarked();
    }

    @Override
    public boolean isEmpty() {
        return getSnapshot().isEmpty();
    }

    @Override
    public Iterator<T> iterator() {
        return getSnapshot().iterator();
    }

    private List<T> getSnapshot() {
        while (true) {
            final List<Node<T>> firstSnap = collect();
            final List<Node<T>> secondSnap = collect();
            if (areSnapshotsEqual(firstSnap, secondSnap)) {
                return firstSnap.stream().map(node -> node.value).collect(Collectors.toList());
            }
        }
    }

    private boolean areSnapshotsEqual(final List<Node<T>> first, final List<Node<T>> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i) != second.get(i)) {
                return false;
            }
        }
        return true;
    }

    private List<Node<T>> collect() {
        final List<Node<T>> list = new ArrayList<>();
        Node<T> curr = head.next.getReference();
        while (curr != null) {
            final Node<T> value = curr;
            if (!curr.next.isMarked()) {
                list.add(value);
            }
            curr = curr.next.getReference();
        }
        return list;
    }
}
