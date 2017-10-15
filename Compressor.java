import java.util.*;
import java.util.stream.Collectors;

class Coordinate
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
}

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

class InlinePixels
{
    Coordinate from;

    Coordinate to;

    enum Orientation
    {
        SINGLE, HORIZONTAL, VERTICAL
    }

    InlinePixels(Coordinate from, Coordinate to)
    {
        this.from = from;
        this.to = to;
    }

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
}

public class Compressor
{
    public Image image;

    public Drawing drawing;

    public HashSet<Coordinate> drawnCoordinates;

    public Coordinate cursor = new Coordinate(0, 0);

    public List<Integer> colors;

    public int currentColorIndex;

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

        colors = colorsCount.entrySet().stream()
                .sorted((c1, c2) -> Integer.compare(c2.getValue(), c1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Takes most present color
        int background = Collections.max(colorsCount.entrySet(), Map.Entry.comparingByValue()).getKey();
        this.nextColor();
        this.drawing = new Drawing(h, w, background);
        this.drawnCoordinates = new HashSet<>();
    }

    public Drawing compress()
    {
        Set<Coordinate> drawableCoordinates = this.getDrawableCoordinates();

        int limit = 10000;
        int i = 0;
        while (!drawnCoordinates.containsAll(drawableCoordinates) && i < limit) {
            if (isPaused()) {
                continue;
            }

            computeNextCommand();
            i++;
        }

        if (i == limit) {
            System.err.println("/!\\ EXIT BECAUSE OF PRESUMED INFINITE LOOP /!\\");
            System.exit(1);
        }

        return drawing;
    }

    public int getCurrentColor()
    {
        return colors.get(currentColorIndex);
    }

    public void nextColor()
    {
        currentColorIndex++;
    }

    public boolean isPaused()
    {
        return false;
    }

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

    private void computeNextCommand()
    {
        DirectionLength dl = this.getBestDirectionLength(cursor, 1);

        if (null == dl) {
            boolean result = this.computeNearestStandalone();

            if (!result) {
                nextColor();
            }
        } else {
            addCommand(dl.direction, dl.length, true, getCurrentColor());
        }
    }

    private boolean computeNearestStandalone()
    {
        Set<Coordinate> standaloneCoordinates = this.getDrawableCoordinates();
        standaloneCoordinates.removeAll(drawnCoordinates);

        HashMap<Coordinate, Double> coordinateDistance = new HashMap<>();
        for (Coordinate c : standaloneCoordinates) {
            if (image.get(c.x, c.y) == getCurrentColor()) {
                double distance = getCostGoTo(c);
                coordinateDistance.put(c, distance);
            }
        }

        if (coordinateDistance.size() == 0) {
            return false;
        }

        Coordinate closest = Collections.min(coordinateDistance.entrySet(), Map.Entry.comparingByValue()).getKey();

        InlinePixels ip = this.computeBestInlinePixels(closest);

        Coordinate target = computeBestLocationForDrawing(ip);

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

    private InlinePixels computeBestInlinePixels(Coordinate coordinate)
    {
        Map<Direction, Integer> neighbours = calculateNeighboursLengths(coordinate, 1);

        int lengthUp = neighbours.get(Direction.UP);
        int lengthDown = neighbours.get(Direction.DOWN);
        int lengthLeft = neighbours.get(Direction.LEFT);
        int lengthRight = neighbours.get(Direction.RIGHT);

        int horizontalLength = lengthLeft + lengthRight;
        int verticalLength = lengthUp + lengthDown;

        if (horizontalLength > verticalLength) {
            return new InlinePixels(
                    new Coordinate(coordinate.x - lengthLeft, coordinate.y),
                    new Coordinate(coordinate.x + lengthRight, coordinate.y)
            );
        } else {
            return new InlinePixels(
                    new Coordinate(coordinate.x, coordinate.y - lengthUp),
                    new Coordinate(coordinate.x, coordinate.y + lengthDown)
            );
        }
    }

    private Coordinate computeBestLocationForDrawing(InlinePixels ip)
    {
        Map<Coordinate, Integer> locations = new HashMap<>();

        if (ip.getOrientation() == InlinePixels.Orientation.HORIZONTAL) {
            Coordinate c1;
            Coordinate c2;
            if (ip.from.x < ip.to.x) {
                c1 = new Coordinate(ip.from.x - 1, ip.from.y);
                c2 = new Coordinate(ip.to.x + 1, ip.to.y);
            } else {
                c1 = new Coordinate(ip.from.x + 1, ip.from.y);
                c2 = new Coordinate(ip.to.x - 1, ip.to.y);
            }
            locations.put(c1, getCostGoTo(c1));
            locations.put(c2, getCostGoTo(c2));
        } else if (ip.getOrientation() == InlinePixels.Orientation.VERTICAL) {
            Coordinate c1;
            Coordinate c2;
            if (ip.from.y < ip.to.y) {
                c1 = new Coordinate(ip.from.x, ip.from.y - 1);
                c2 = new Coordinate(ip.to.x, ip.to.y + 1);
            } else {
                c1 = new Coordinate(ip.from.x, ip.from.y + 1);
                c2 = new Coordinate(ip.to.x, ip.to.y - 1);
            }
            locations.put(c1, getCostGoTo(c1));
            locations.put(c2, getCostGoTo(c2));
        } else if (ip.getOrientation() == InlinePixels.Orientation.SINGLE) {
            Coordinate c1 = new Coordinate(ip.from.x + 1, ip.from.y);
            Coordinate c2 = new Coordinate(ip.from.x - 1, ip.from.y);
            Coordinate c3 = new Coordinate(ip.from.x, ip.from.y + 1);
            Coordinate c4 = new Coordinate(ip.from.x, ip.from.y - 1);

            locations.put(c1, getCostGoTo(c1));
            locations.put(c2, getCostGoTo(c2));
            locations.put(c3, getCostGoTo(c3));
            locations.put(c4, getCostGoTo(c4));
        }

        locations = locations.entrySet().stream()
                .filter(e -> isWithinBounds(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (locations.size() == 0) {
            if (ip.getOrientation() == InlinePixels.Orientation.HORIZONTAL) {
                Coordinate c1 = new Coordinate(ip.from.x, ip.from.y - 1);
                Coordinate c2 = new Coordinate(ip.from.x, ip.from.y + 1);
                Coordinate c3 = new Coordinate(ip.to.x, ip.to.y - 1);
                Coordinate c4 = new Coordinate(ip.to.x, ip.to.y + 1);
                locations.put(c1, getCostGoTo(c1));
                locations.put(c2, getCostGoTo(c2));
                locations.put(c3, getCostGoTo(c3));
                locations.put(c4, getCostGoTo(c4));
            } else if (ip.getOrientation() == InlinePixels.Orientation.VERTICAL) {
                Coordinate c1 = new Coordinate(ip.from.x - 1, ip.from.y);
                Coordinate c2 = new Coordinate(ip.from.x + 1, ip.from.y);
                Coordinate c3 = new Coordinate(ip.to.x - 1, ip.to.y);
                Coordinate c4 = new Coordinate(ip.to.x + 1, ip.to.y);
                locations.put(c1, getCostGoTo(c1));
                locations.put(c2, getCostGoTo(c2));
                locations.put(c3, getCostGoTo(c3));
                locations.put(c4, getCostGoTo(c4));
            }
        }

        locations = locations.entrySet().stream()
                .filter(e -> isWithinBounds(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return Collections.min(locations.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private boolean isWithinBounds(Coordinate c)
    {
        return c.x >= 0 && c.x <= image.getWidth() && c.y >= 0 && c.y < image.getHeight();
    }

    protected void addCommand(Direction direction, int distance, boolean paint, int color)
    {
        StringBuilder sb = new StringBuilder(direction.toString() + " " + distance);

        if (paint) {
            sb.append(" " + Integer.toString(color, 16));
        }

        drawing.addCommand(new DrawingCommand(sb.toString()));

        if (direction == Direction.UP || direction == Direction.DOWN) {
            boolean forward = direction == Direction.DOWN;
            if (paint) {
                if (forward) {
                    for (int py = cursor.y; py < cursor.y + distance; py++) {
                        drawnCoordinates.add(new Coordinate(cursor.x, py + 1));
                    }
                } else {
                    for (int py = cursor.y; py > cursor.y - distance; py--) {
                        drawnCoordinates.add(new Coordinate(cursor.x, py - 1));
                    }
                }
            }

            if (forward) {
                cursor.y += distance;
            } else {
                cursor.y -= distance;
            }
        } else if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            boolean forward = direction == Direction.RIGHT;
            if (paint) {
                if (forward) {
                    for (int px = cursor.x; px < cursor.x + distance; px++) {
                        drawnCoordinates.add(new Coordinate(px + 1, cursor.y));
                    }
                } else {
                    for (int px = cursor.x; px > cursor.x - distance; px--) {
                        drawnCoordinates.add(new Coordinate(px - 1, cursor.y));
                    }
                }
            }

            if (forward) {
                cursor.x += distance;
            } else {
                cursor.x -= distance;
            }
        }
    }

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

    private Map<Direction, Integer> calculateNeighboursLengths(Coordinate coordinate, int offset)
    {
        HashMap<Direction, Integer> directions = new HashMap<>();

        directions.put(Direction.UP, this.calculateDirectionLength(coordinate, Direction.UP, offset));
        directions.put(Direction.RIGHT, this.calculateDirectionLength(coordinate, Direction.RIGHT, offset));
        directions.put(Direction.DOWN, this.calculateDirectionLength(coordinate, Direction.DOWN, offset));
        directions.put(Direction.LEFT, this.calculateDirectionLength(coordinate, Direction.LEFT, offset));

        return directions;
    }

    @SuppressWarnings("Duplicates")
    private int calculateDirectionLength(Coordinate coordinate, Direction direction, int offset)
    {
        int x = coordinate.x;
        int y = coordinate.y;

        double containsCap = 70 / 100;

        if (direction == Direction.UP || direction == Direction.DOWN) {
            int incr = direction == Direction.UP ? -1 : 1;

            int initialColor;
            try {
                initialColor = image.get(x, y + (incr * offset));
            } catch (ArrayIndexOutOfBoundsException e) {
                return 0;
            }

            if (initialColor != getCurrentColor()) {
                return 0;
            }

            int i = -1;
            int color;
            int containsCount = 0;
            int lastIBeforeContains = i;
            while (true) {
                i++;

                int newY = y + (incr * (i + offset));
                Coordinate newC = new Coordinate(x, newY);

                try {
                    color = image.get(x, newY);
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }

                if (color == drawing.background) {
                    break;
                }

                if (initialColor != color) {
                    if (!drawnCoordinates.contains(newC)) {
                        continue;
                    } else {
                        break;
                    }
                }

                if (drawnCoordinates.contains(newC)) {
                    containsCount++;
                    if (lastIBeforeContains == -1) {
                        lastIBeforeContains = i;
                    }
                }
            }

            if (i > 0 && containsCount / i > containsCap) {
                return lastIBeforeContains;
            }

            return i;
        } else if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            int incr = direction == Direction.LEFT ? -1 : 1;

            int initialColor;
            try {
                initialColor = image.get(x + (incr * offset), y);
            } catch (ArrayIndexOutOfBoundsException e) {
                return 0;
            }

            if (initialColor != getCurrentColor()) {
                return 0;
            }


            int i = -1;
            int color;
            int containsCount = 0;
            int lastIBeforeContains = i;
            while (true) {
                i++;

                int newX = x + (incr * (i + offset));
                Coordinate newC = new Coordinate(newX, y);

                try {
                    color = image.get(newX, y);
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }

                if (color == drawing.background) {
                    break;
                }

                if (initialColor != color) {
                    if (!drawnCoordinates.contains(newC)) {
                        continue;
                    }

                    break;
                }

                if (drawnCoordinates.contains(newC)) {
                    containsCount++;
                    if (lastIBeforeContains == -1) {
                        lastIBeforeContains = i;
                    }
                }
            }

            if (i > 0 && containsCount / i > containsCap) {
                return lastIBeforeContains;
            }

            return i;
        }

        return -1;
    }
}
