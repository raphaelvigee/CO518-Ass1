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
            return Objects.equals(c.toString(), this.toString());
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

class DirectionCost
{
    Direction direction;

    int cost;

    DirectionCost(Direction direction, int cost)
    {
        this.direction = direction;
        this.cost = cost;
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
        assert from.x == to.x || from.y == to.y;

        this.from = from;
        this.to = to;
    }

    public Orientation getOrientation()
    {
        if (from == to) {
            return Orientation.SINGLE;
        }

        if (from.x == to.x) {
            return Orientation.HORIZONTAL;
        }

        if (from.y == to.y) {
            return Orientation.VERTICAL;
        }

        return null;
    }
}

public class Compressor
{
    private Image image;

    private Drawing drawing;

    private int backgroundColor;

    private HashSet<Coordinate> drawnCoordinates;

    private Coordinate cursor = new Coordinate(0, 0);

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
                colorsCount.put(p, pixelCount);
            }
        }

        // Takes most present color
        this.backgroundColor = Collections.max(colorsCount.entrySet(), Map.Entry.comparingByValue()).getKey();
        this.drawing = new Drawing(h, w, this.backgroundColor);
        this.drawnCoordinates = new HashSet<>();
    }

    public Drawing compress()
    {
        Set<Coordinate> drawableCoordinates = this.getDrawableCoordinates();

        int limit = 10000;
        int i = 0;
        while (!drawnCoordinates.containsAll(drawableCoordinates) && i < limit) {
            computeNextCommand();
            i++;
        }

        if (i == limit) {
            System.err.println("/!\\ EXIT BECAUSE OF PRESUMED INFINITE LOOP /!\\");
            System.exit(1);
        }

        return drawing;
    }

    private Set<Coordinate> getDrawableCoordinates()
    {
        HashSet<Coordinate> drawableCoordinates = new HashSet<>();
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int c = image.getPixels()[y][x];
                if (c != backgroundColor) {
                    drawableCoordinates.add(new Coordinate(x, y));
                }
            }
        }

        return drawableCoordinates;
    }

    private void computeNextCommand()
    {
        DirectionCost dc = this.getBestDirectionCost(cursor, 1);

        if (null == dc) {
            this.computeNearestStandalone();
        } else {
            int color = getColorForDirection(cursor, dc.direction);
            addCommand(dc.direction, dc.cost, true, color);
        }
    }

    private int getColorForDirection(Coordinate coordinate, Direction direction)
    {
        if (direction == Direction.UP) {
            return image.get(coordinate.x, coordinate.y - 1);
        } else if (direction == Direction.DOWN) {
            return image.get(coordinate.x, coordinate.y + 1);
        } else if (direction == Direction.LEFT) {
            return image.get(coordinate.x - 1, coordinate.y);
        } else if (direction == Direction.RIGHT) {
            return image.get(coordinate.x + 1, coordinate.y);
        }

        return -1;
    }

    private void computeNearestStandalone()
    {
        Set<Coordinate> standaloneCoordinates = this.getDrawableCoordinates();
        standaloneCoordinates.removeAll(drawnCoordinates);

        HashMap<Coordinate, Double> coordinateDistance = new HashMap<>();
        for (Coordinate c : standaloneCoordinates) {
            double distance = getCostGoTo(c);
            coordinateDistance.put(c, distance);
        }

        Coordinate closest = Collections.min(coordinateDistance.entrySet(), Map.Entry.comparingByValue()).getKey();

        InlinePixels ip = this.computeBestInlinePixels(closest);

        int costFrom = getCostGoTo(ip.from);
        int costTo = getCostGoTo(ip.to);

        Coordinate bldFrom = computeBestLocationForDrawing(ip.from);
        Coordinate bldTo = computeBestLocationForDrawing(ip.to);

        int costDrawingFrom = getCostGoTo(bldFrom);
        int costDrawingTo = getCostGoTo(bldTo);

        Map<Coordinate, Integer> costs = new HashMap<>();
        costs.put(bldFrom, costDrawingFrom);
        costs.put(bldTo, costDrawingTo);
        costs.put(ip.from, costFrom);
        costs.put(ip.to, costTo);

        costs = costs.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Coordinate target = Collections.min(costs.entrySet(), Map.Entry.comparingByValue()).getKey();

        int distanceX = target.x - cursor.x;
        int distanceY = target.y - cursor.y;

        int offsetX = 0;
        int offsetY = 0;

        if ((target == ip.from || target == ip.to) && target != cursor) {
            if (distanceX > 0) {
                offsetX = -1;
            } else if (distanceX < 0) {
                offsetX = 1;
            }

            if (offsetX == 0) {
                if (distanceY > 0) {
                    offsetY = -1;
                } else if (distanceY < 0) {
                    offsetY = 1;
                }
            }

            if (distanceX != 0 && isWithinBounds(new Coordinate(cursor.x + distanceX + offsetX, cursor.y))) {
                distanceX += offsetX;
            }

            if (distanceY != 0 && isWithinBounds(new Coordinate(cursor.x, cursor.y + distanceY + offsetY))) {
                distanceY += offsetY;
            }
        }

        if (distanceX != 0) {
            this.addCommand(distanceX < 0 ? Direction.LEFT : Direction.RIGHT, Math.abs(distanceX), false, 0);
        }

        if (distanceY != 0) {
            this.addCommand(distanceY < 0 ? Direction.UP : Direction.DOWN, Math.abs(distanceY), false, 0);
        }
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
        int initialColor = image.get(coordinate.x, coordinate.y);

        Map<Direction, Integer> neighbours = calculateNeighboursCosts(coordinate, 0);

        int verticalCost = 0;
        int costUp = neighbours.get(Direction.UP);
        if (costUp > 0) {
            verticalCost += costUp + 1;
        }
        int costDown = neighbours.get(Direction.DOWN);
        if (costDown > 0) {
            verticalCost += costDown + 1;
        }

        int horizontalCost = 0;
        int costLeft = neighbours.get(Direction.LEFT);
        if (costLeft > 0) {
            horizontalCost += costLeft + 1;
        }
        int costRight = neighbours.get(Direction.RIGHT);
        if (costRight > 0) {
            horizontalCost += costRight + 1;
        }

        if (verticalCost > horizontalCost) {
            // Vertical
            int i;
            int color;

            i = 0;
            do {
                i--;
                try {
                    color = image.get(coordinate.x, coordinate.y + i);
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            } while (initialColor == color);

            Coordinate from = new Coordinate(coordinate.x, coordinate.y + (i + 1));

            i = 0;
            do {
                i++;
                try {
                    color = image.get(coordinate.x, coordinate.y + i);
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            } while (initialColor == color);

            Coordinate to = new Coordinate(coordinate.x, coordinate.y + (i - 1));

            return new InlinePixels(from, to);
        } else {
            // Horizontal

            int i;
            int color;

            i = 0;
            do {
                i--;
                try {
                    color = image.get(coordinate.x + i, coordinate.y);
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            } while (initialColor == color);

            Coordinate from = new Coordinate(coordinate.x + (i + 1), coordinate.y);

            i = 0;
            do {
                i++;
                try {
                    color = image.get(coordinate.x + i, coordinate.y);
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            } while (initialColor == color);

            Coordinate to = new Coordinate(coordinate.x + (i - 1), coordinate.y);

            return new InlinePixels(from, to);
        }
    }

    private Coordinate computeBestLocationForDrawing(Coordinate coordinate)
    {
        Coordinate up = new Coordinate(coordinate.x, coordinate.y - 1);
        Coordinate down = new Coordinate(coordinate.x, coordinate.y + 1);
        Coordinate left = new Coordinate(coordinate.x - 1, coordinate.y);
        Coordinate right = new Coordinate(coordinate.x + 1, coordinate.y);

        if (isWithinBounds(up)) {
            return up;
        } else if (isWithinBounds(down)) {
            return down;
        } else if (isWithinBounds(left)) {
            return left;
        } else if (isWithinBounds(right)) {
            return right;
        }

        System.err.println("NO SUITABLE LOCATION AVAILABLE FOR DRAWING " + coordinate);

        return null;
    }

    private boolean isWithinBounds(Coordinate c)
    {
        return c.x >= 0 && c.x <= image.getWidth() && c.y >= 0 && c.y < image.getHeight();
    }

    private void addCommand(Direction direction, int distance, boolean paint, int color)
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

    private DirectionCost getBestDirectionCost(Coordinate coordinate, int offset)
    {
        Map<Direction, Integer> neighbours = calculateNeighboursCosts(coordinate, offset);

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

        return new DirectionCost(pair.getKey(), pair.getValue());
    }

    private Map<Direction, Integer> calculateNeighboursCosts(Coordinate coordinate, int offset)
    {
        HashMap<Direction, Integer> directions = new HashMap<>();

        directions.put(Direction.UP, this.calculateDirectionCost(coordinate, Direction.UP, offset));
        directions.put(Direction.RIGHT, this.calculateDirectionCost(coordinate, Direction.RIGHT, offset));
        directions.put(Direction.DOWN, this.calculateDirectionCost(coordinate, Direction.DOWN, offset));
        directions.put(Direction.LEFT, this.calculateDirectionCost(coordinate, Direction.LEFT, offset));

        return directions;
    }

    @SuppressWarnings("Duplicates")
    private int calculateDirectionCost(Coordinate coordinate, Direction direction, int offset)
    {
        int x = coordinate.x;
        int y = coordinate.y;

        if (direction == Direction.UP || direction == Direction.DOWN) {
            int incr = direction == Direction.UP ? -1 : 1;

            int initialColor;
            try {
                initialColor = image.get(x, y + (incr * offset));
            } catch (ArrayIndexOutOfBoundsException e) {
                return -999;
            }

            if (initialColor == backgroundColor) {
                return -1;
            }

            int i = -1;
            int color;
            do {
                i++;
                int newY = y + (incr * (i + offset));
                try {
                    color = image.get(x, newY);
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }

                if (drawnCoordinates.contains(new Coordinate(x, newY))) {
                    break;
                }
            } while (initialColor == color);

            return i;
        } else if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            int incr = direction == Direction.LEFT ? -1 : 1;

            int initialColor;
            try {
                initialColor = image.get(x + (incr * offset), y);
            } catch (ArrayIndexOutOfBoundsException e) {
                return -999;
            }

            if (initialColor == backgroundColor) {
                return -1;
            }

            int i = -1;
            int color;
            do {
                i++;
                int newX = x + (incr * (i + offset));
                try {
                    color = image.get(newX, y);
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }

                if (drawnCoordinates.contains(new Coordinate(newX, y))) {
                    break;
                }
            } while (initialColor == color);

            return i;
        }

        return -1;
    }
}
