import battlecode.common.*;
public interface Unit {
    protected final RobotController robotController;
    public Unit(RobotController robotController) {
        this.robotController = robotController;
    }

    public void executeTurn();
}