package steamlocomotive;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public class DynamicArray<T> implements Iterable<T>{

    private T[] content;
    private int size;

    /**
     * Pass in a guess for the max size of the dynamic array
     */
    public DynamicArray(int initSize) {
        content = (T[]) new Object[initSize];
        size = 0;
    }

    /**
     * Return the size of the array
     */
    public int size() {
        return size;
    }

    /**
     * Add an object to the array
     */
    public void add(T obj) {
        if (size < content.length) {
            content[size] = obj;
        } else {

            T[] larger = (T[]) new Object[content.length * 2];
            for (int index = 0; index < content.length; index++) {
                larger[index] = content[index];
            }
            larger[size] = obj;
            content = larger;
        }
        size++;
    }

    /**
     * Get an object at an index in the array
     */
    public T get(int index) {
        if (index < size) {
            return content[index];
        }
        throw new IndexOutOfBoundsException();
    }

    /**
     * Efficiently removes an index from the array, but will not maintain order
     */
    public void removeQuick(int index) {
        if(index>=size) {
            throw new IndexOutOfBoundsException();
        }
        content[index] = content[size-1];
        size--;
    }

    /**
     * Less efficiently removes an index from the array, but maintains order
     */
    public void removeKeepOrder(int index) {
        if(index>=size) {
            throw new IndexOutOfBoundsException();
        }
        for(int i = index; i < size; i++) {
            content[i] = content[i+1];
        }
        size--;
    }

    @Override
    public Iterator iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(Consumer action) {
        for(int index = 0; index < size; index++) {
            action.accept(content[index]);
        }
    }

    @Override
    public Spliterator spliterator() {
        throw new UnsupportedOperationException();
    }
}
