import java.util.*;
import java.util.stream.Collectors;

/**
 * Store Coordinate
 */
class Coordinate implements Cloneable
{
    int x;

    int y;

    Coordinate(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object v)
    {
        if (v instanceof Coordinate) {
            Coordinate c = (Coordinate) v;
            return c.x == this.x && c.y == this.y;
        }

        return false;
    }

    @Override
    public int hashCode()
    {
        return x * y + y;
    }

    @Override
    public String toString()
    {
        return x + "," + y;
    }

    /**
     * Get neighbour Coordinate in Direction
     *
     * @param direction The Direction
     * @return The neighbour
     */
    public Coordinate getNeighbour(Direction direction)
    {
        if (direction == Direction.UP) {
            return new Coordinate(this.x, this.y - 1);
        }

        if (direction == Direction.RIGHT) {
            return new Coordinate(this.x + 1, this.y);
        }

        if (direction == Direction.DOWN) {
            return new Coordinate(this.x, this.y + 1);
        }

        if (direction == Direction.LEFT) {
            return new Coordinate(this.x - 1, this.y);
        }

        return null;
    }

    public Coordinate clone()
    {
        try {
            return (Coordinate) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}

/**
 * Stores a pair Direction / run-length
 */
class DirectionLength
{
    Direction direction;

    int length;

    DirectionLength(Direction direction, int length)
    {
        this.direction = direction;
        this.length = length;
    }
}

/**
 * Stores a representation of a line of pixels
 */
class InlinePixels
{
    Coordinate from;

    Coordinate to;

    InlinePixels(Coordinate from, Coordinate to)
    {
        this.from = from;
        this.to = to;
    }

    /**
     * @return The InlinePixels Orientation
     */
    public Orientation getOrientation()
    {
        if (from.equals(to)) {
            return Orientation.SINGLE;
        }

        if (from.x == to.x) {
            return Orientation.VERTICAL;
        }

        if (from.y == to.y) {
            return Orientation.HORIZONTAL;
        }

        return null;
    }

    /**
     * @return The InlinePixels length
     */
    public int length()
    {
        return Math.abs((from.x - to.x) + (from.y - to.y)) + 1;
    }

    /**
     * @param image The Image
     * @param color The color
     * @return Wheter if the InlinePixels contains the color
     */
    public boolean contains(Image image, int color)
    {
        if (getOrientation() == Orientation.SINGLE) {
            return image.get(from) == color;
        }

        if (getOrientation() == Orientation.VERTICAL) {
            boolean containsColor = false;
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.get(from.x, y) == color) {
                    containsColor = true;
                }
            }

            return containsColor;
        }

        if (getOrientation() == Orientation.HORIZONTAL) {
            boolean containsColor = false;
            for (int x = 0; x < image.getHeight(); x++) {
                if (image.get(x, from.y) == color) {
                    containsColor = true;
                }
            }

            return containsColor;
        }

        return false;
    }

    /**
     * @return The InlinePixels Direction
     */
    public Direction getDirection()
    {
        if (getOrientation() == Orientation.HORIZONTAL) {
            if (from.x < to.x) {
                return Direction.RIGHT;
            } else {
                return Direction.LEFT;
            }
        } else if (getOrientation() == Orientation.VERTICAL) {
            if (from.y < to.y) {
                return Direction.DOWN;
            } else {
                return Direction.UP;
            }
        }

        return null;
    }
}

/**
 * The Compressor
 */
public class Compressor
{
    public Image image;

    public Drawing drawing;

    public HashSet<Coordinate> drawnCoordinates;

    public HashSet<Integer> drawnColors;

    public Coordinate cursor = new Coordinate(0, 0);

    public List<Integer> colors;

    public int currentColorIndex = 0;

    Compressor(Image image)
    {
        this.image = image;

        int h = image.getHeight();
        int w = image.getWidth();

        // Determine backgroundColor
        HashMap<Integer, Integer> colorsCount = new HashMap<>();
        for (int[] row : image.getPixels()) {
            for (int p : row) {
                int pixelCount = 0;
                if (colorsCount.containsKey(p)) {
                    pixelCount = colorsCount.get(p);
                }
                pixelCount++;
                colorsCount.put(p, pixelCount);
            }
        }

        // Extract individual colors ordered by importance
        colors = colorsCount.entrySet().stream()
                .sorted((c1, c2) -> Integer.compare(c2.getValue(), c1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Takes most present color
        int background = Collections.max(colorsCount.entrySet(), Map.Entry.comparingByValue()).getKey();
        this.drawing = new Drawing(h, w, background);
        this.drawnCoordinates = new HashSet<>();
        this.drawnColors = new HashSet<>();
        this.nextColor();
    }

    /**
     * Runs the compression
     * `i` prevents infinite loops
     *
     * @return The Drawing
     */
    public Drawing compress()
    {
        int limit = 10000;
        int i = 0;
        while (!isDone() && i < limit) {
            computeNextCommand();
            i++;
        }

        if (i == limit) {
            System.err.println("/!\\ EXIT BECAUSE OF PRESUMED INFINITE LOOP /!\\");
        }

        return drawing;
    }

    /**
     * Get current color
     *
     * @return The current color
     */
    public int getCurrentColor()
    {
        return colors.get(currentColorIndex);
    }

    /**
     * Triggers next color from the pool
     */
    public void nextColor()
    {
        drawnColors.add(colors.get(currentColorIndex));

        if (currentColorIndex + 1 < colors.size()) {
            currentColorIndex++;
        }
    }

    /**
     * Cleanup drawnCoordinates from coordinates for which the color has not been processed yet
     *
     * @return Nothing has been removed
     */
    public boolean cleanDrawnCoordinates()
    {
        return !drawnCoordinates.removeIf(c -> !drawnColors.contains(image.get(c)) && image.get(c) != getCurrentColor());
    }

    /**
     * @param color Color to test
     * @return Is color fully drawn
     */
    public boolean allColorDrawn(int color)
    {
        boolean allDrawn = true;

        for (Coordinate c : getDrawableCoordinates()) {
            if (image.get(c) == color) {
                if (!drawnCoordinates.contains(c)) {
                    allDrawn = false;
                }
            }
        }

        return allDrawn;
    }

    /**
     * Compute coordinates not being part of the background
     *
     * @return The not drawn coordinates
     */
    private Set<Coordinate> getDrawableCoordinates()
    {
        HashSet<Coordinate> drawableCoordinates = new HashSet<>();
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int c = image.get(x, y);
                if (c != drawing.background) {
                    drawableCoordinates.add(new Coordinate(x, y));
                }
            }
        }

        return drawableCoordinates;
    }

    /**
     * Compute next command
     */
    protected void computeNextCommand()
    {
        DirectionLength dl = this.getBestDirectionLength(cursor, 1);

        if (null == dl) {
            boolean result = this.computeNearestStandalone();

            if (!result) {
                if (allColorDrawn(getCurrentColor())) {
                    cleanDrawnCoordinates();
                    nextColor();
                }
            }
        } else {
            addCommand(dl.direction, dl.length, true, getCurrentColor());
        }

        if (isDone()) {
            cleanDrawnCoordinates();
        }
    }

    /**
     * @return Is the image fully drawn
     */
    private boolean isDone()
    {
        return drawnCoordinates.containsAll(getDrawableCoordinates());
    }

    /**
     * Compute commands for moving to the "lest expensive" next location
     *
     * @return Whether if it moved successfully or not
     */
    private boolean computeNearestStandalone()
    {
        Set<Coordinate> standaloneCoordinates = this.getDrawableCoordinates();
        standaloneCoordinates.removeAll(drawnCoordinates);

        // Used to store an InlinePixels, a target Coordinate and its cost
        class InlinePixelsTargetCost
        {
            InlinePixels ip;

            Coordinate target;

            int cost;

            public InlinePixelsTargetCost(InlinePixels ip, Coordinate target, int cost)
            {
                this.ip = ip;
                this.target = target;
                this.cost = cost;
            }
        }

        List<InlinePixelsTargetCost> targetScores = new ArrayList<>();
        for (Coordinate c : standaloneCoordinates) {
            if (image.get(c) == getCurrentColor()) {
                InlinePixels ip = this.computeBestInlinePixels(c);

                if (ip.contains(image, getCurrentColor())) {
                    Coordinate target = computeBestLocationForDrawing(ip);

                    int cost = getCostGoTo(target);

                    InlinePixelsTargetCost iptc = new InlinePixelsTargetCost(ip, target, cost);

                    targetScores.add(iptc);
                }
            }
        }

        if (targetScores.size() == 0) {
            return false;
        }

        Comparator<InlinePixelsTargetCost> cmp = (o1, o2) -> Integer.valueOf(o2.cost).compareTo(o1.cost);
        Coordinate target = Collections.max(targetScores, cmp).target;

        int distanceX = target.x - cursor.x;
        int distanceY = target.y - cursor.y;

        if (distanceX != 0) {
            this.addCommand(distanceX < 0 ? Direction.LEFT : Direction.RIGHT, Math.abs(distanceX), false, 0);
        }

        if (distanceY != 0) {
            this.addCommand(distanceY < 0 ? Direction.UP : Direction.DOWN, Math.abs(distanceY), false, 0);
        }

        return true;
    }

    /**
     * Get "cost" in terms of number of commands to move to the coordinate
     *
     * @param c The coordinate
     * @return The cost
     */
    private int getCostGoTo(Coordinate c)
    {
        int cost = 0;

        if (cursor.x != c.x) {
            cost++;
        }

        if (cursor.y != c.y) {
            cost++;
        }

        return cost;
    }

    /**
     * Compute best InlinePixels for coordinate
     *
     * @param coordinate The coordinate
     * @return The InlinePixel
     */
    private InlinePixels computeBestInlinePixels(Coordinate coordinate)
    {
        Map<Direction, Integer> neighbours = calculateNeighboursLengths(coordinate, 1);

        int lengthUp = neighbours.get(Direction.UP);
        int lengthDown = neighbours.get(Direction.DOWN);
        int lengthLeft = neighbours.get(Direction.LEFT);
        int lengthRight = neighbours.get(Direction.RIGHT);

        int horizontalLength = lengthLeft + lengthRight;
        int verticalLength = lengthUp + lengthDown;

        InlinePixels horizontal = new InlinePixels(
                new Coordinate(coordinate.x - lengthLeft, coordinate.y),
                new Coordinate(coordinate.x + lengthRight, coordinate.y)
        );

        InlinePixels vertical = new InlinePixels(
                new Coordinate(coordinate.x, coordinate.y - lengthUp),
                new Coordinate(coordinate.x, coordinate.y + lengthDown)
        );

        if (horizontalLength == verticalLength) {
            int horizontalCost = getCostGoTo(computeBestLocationForDrawing(horizontal));
            int verticalCost = getCostGoTo(computeBestLocationForDrawing(vertical));

            if (horizontalCost < verticalCost) {
                return horizontal;
            } else {
                return vertical;
            }
        } else if (horizontalLength > verticalLength) {
            return horizontal;
        } else {
            return vertical;
        }
    }

    /**
     * Returns best coordinate for drawing InlinePixels
     *
     * @param ip The InlinePixels
     * @return The best Coordinate
     */
    private Coordinate computeBestLocationForDrawing(InlinePixels ip)
    {
        List<Coordinate> locations = new ArrayList<>();

        if (ip.getOrientation() == Orientation.HORIZONTAL) {
            if (ip.getDirection() == Direction.RIGHT) {
                locations.add(ip.from.getNeighbour(Direction.LEFT));
                locations.add(ip.to.getNeighbour(Direction.RIGHT));
            } else {
                locations.add(ip.from.getNeighbour(Direction.RIGHT));
                locations.add(ip.to.getNeighbour(Direction.LEFT));
            }
        } else if (ip.getOrientation() == Orientation.VERTICAL) {
            if (ip.getDirection() == Direction.DOWN) {
                locations.add(ip.from.getNeighbour(Direction.UP));
                locations.add(ip.to.getNeighbour(Direction.DOWN));
            } else {
                locations.add(ip.from.getNeighbour(Direction.DOWN));
                locations.add(ip.to.getNeighbour(Direction.UP));
            }
        } else if (ip.getOrientation() == Orientation.SINGLE) {
            locations.add(ip.from.getNeighbour(Direction.UP));
            locations.add(ip.from.getNeighbour(Direction.RIGHT));
            locations.add(ip.from.getNeighbour(Direction.DOWN));
            locations.add(ip.from.getNeighbour(Direction.LEFT));
        }

        // Used to store a Coordinate and its cost
        class CoordinateCost
        {
            Coordinate coordinate;

            int cost;

            public CoordinateCost(Coordinate coordinate, int cost)
            {
                this.coordinate = coordinate;
                this.cost = cost;
            }
        }

        List<CoordinateCost> coordinatesCost = locations.stream()
                .map(c -> new CoordinateCost(c, getCostGoTo(c)))
                .collect(Collectors.toList());

        Comparator<CoordinateCost> cmp = (o1, o2) -> Integer.valueOf(o2.cost).compareTo(o1.cost);

        return Collections.max(coordinatesCost, cmp).coordinate;
    }

    /**
     * @param c The Coordinate
     * @return Is the Coordinate within bounds
     */
    private boolean isWithinBounds(Coordinate c)
    {
        return c.x >= 0 && c.x <= image.getWidth() && c.y >= 0 && c.y < image.getHeight();
    }

    /**
     * @param d The Direction
     * @return Is the Direction in the orthogonal direction
     */
    private boolean isForward(Direction d)
    {
        return d == Direction.DOWN || d == Direction.RIGHT;
    }

    /**
     * Return the increment for the Direction
     *
     * @param d The Direction
     * @return The increment (+1 or -1)
     */
    private int getIncr(Direction d)
    {
        return isForward(d) ? 1 : -1;
    }

    /**
     * Add Command to the Drawing object and move the cursor
     *
     * @param direction The Direction
     * @param distance  The distance
     * @param paint     Whether to paint or not
     * @param color     The color to paint
     */
    protected void addCommand(Direction direction, int distance, boolean paint, int color)
    {
        StringBuilder sb = new StringBuilder(direction.toString() + " " + distance);

        if (paint) {
            sb.append(" " + Integer.toString(color, 16));
        }

        drawing.addCommand(new DrawingCommand(sb.toString()));

        boolean forward = isForward(direction);
        int incr = getIncr(direction);

        int relativeDistance = distance * incr;

        if (direction.getOrientation() == Orientation.VERTICAL) {
            if (paint) {
                for (int py = cursor.y; forward ? (py < cursor.y + relativeDistance) : (py > cursor.y + relativeDistance); py += incr) {
                    drawnCoordinates.add(new Coordinate(cursor.x, py + incr));
                }
            }

            cursor.y += relativeDistance;
        } else if (direction.getOrientation() == Orientation.HORIZONTAL) {
            if (paint) {
                for (int px = cursor.x; forward ? (px < cursor.x + relativeDistance) : (px > cursor.x + relativeDistance); px += incr) {
                    drawnCoordinates.add(new Coordinate(px + incr, cursor.y));
                }
            }

            cursor.x += relativeDistance;
        }
    }

    /**
     * @param coordinate The Coordinate
     * @param offset     The offset
     * @return The best DirectionLength for the coordinate
     */
    private DirectionLength getBestDirectionLength(Coordinate coordinate, int offset)
    {
        Map<Direction, Integer> neighbours = calculateNeighboursLengths(coordinate, offset);

        boolean allImpossible = true;
        for (Map.Entry<Direction, Integer> entry : neighbours.entrySet()) {
            if (entry.getValue() > 0) {
                allImpossible = false;
            }
        }

        if (allImpossible) {
            return null;
        }

        Map.Entry<Direction, Integer> pair = Collections.max(neighbours.entrySet(), Map.Entry.comparingByValue());

        return new DirectionLength(pair.getKey(), pair.getValue());
    }

    /**
     * Get neighbours run-length for Coordinate
     *
     * @param coordinate The Coordinate
     * @param offset     The offset
     * @return A Map of Direction / length
     */
    private Map<Direction, Integer> calculateNeighboursLengths(Coordinate coordinate, int offset)
    {
        HashMap<Direction, Integer> directions = new HashMap<>();

        directions.put(Direction.UP, this.calculateDirectionLength(coordinate, Direction.UP, offset));
        directions.put(Direction.RIGHT, this.calculateDirectionLength(coordinate, Direction.RIGHT, offset));
        directions.put(Direction.DOWN, this.calculateDirectionLength(coordinate, Direction.DOWN, offset));
        directions.put(Direction.LEFT, this.calculateDirectionLength(coordinate, Direction.LEFT, offset));

        return directions;
    }

    /**
     * @param coordinate The Coordinate
     * @param direction  The Direction
     * @param offset     The offset
     * @return run-length of Direction for Coordinate
     */
    private int calculateDirectionLength(Coordinate coordinate, Direction direction, int offset)
    {
        int x = coordinate.x;
        int y = coordinate.y;

        int incr = getIncr(direction);

        int i = -1;
        int color;
        boolean containsCurrentColor = false;
        boolean containsUndrawn = false;
        while (true) {
            i++;

            Coordinate newC;
            if (direction.getOrientation() == Orientation.VERTICAL) {
                int newY = y + (incr * (i + offset));
                newC = new Coordinate(x, newY);
            } else if (direction.getOrientation() == Orientation.HORIZONTAL) {
                int newX = x + (incr * (i + offset));
                newC = new Coordinate(newX, y);
            } else {
                System.err.println("Unhandled Orientation");
                break;
            }

            try {
                color = image.get(newC);
            } catch (ArrayIndexOutOfBoundsException e) {
                break;
            }

            if (color == drawing.background) {
                break;
            }

            if (getCurrentColor() == color) {
                containsCurrentColor = true;

                if (!drawnCoordinates.contains(newC)) {
                    containsUndrawn = true;
                }
            } else {
                if (drawnColors.contains(color)) {
                    break;
                }
            }
        }

        if (!containsCurrentColor || !containsUndrawn) {
            return 0;
        }

        return i;
    }
}
