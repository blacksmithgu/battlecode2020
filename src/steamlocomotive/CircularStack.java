package steamlocomotive;

public class CircularStack<T> {
    T[] content;
    int head;

    public CircularStack(int size) {
        content = (T[]) new Object[size];
        head = 0;
    }

    public void push(T obj) {
        head++;
        head = head % content.length;
        content[head] = obj;
    }

    public T pop() {
        T toReturn = content[head];
        content[head] = null;
        head += content.length;
        head--;
        head = head % content.length;
        return toReturn;
    }
}
