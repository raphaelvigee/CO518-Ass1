import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Objects;

class PixelCellRenderer extends DefaultTableCellRenderer
{
    private static final long serialVersionUID = 1L;

    CompressorDebugger compressor;

    public PixelCellRenderer(CompressorDebugger compressor)
    {
        this.compressor = compressor;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col)
    {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

        JLabel label = (JLabel) c;

        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);

        Font labelFont = label.getFont();

        // Set the label's font size to the newly determined size.
        label.setFont(new Font(labelFont.getName(), Font.PLAIN, 10));

        if (row == 0 || col == 0) {
            label.setBackground(Color.white);
            label.setForeground(Color.black);

            return label;
        }

        if (value != null) {
            Color bgColor = new Color(Image.colours[(int) value]);
            label.setBackground(bgColor);
            label.setForeground(contrastColor(bgColor));
        } else {
            label.setBackground(new Color(0, 0, 0, 0));
            label.setForeground(Color.BLACK);
        }

        if (compressor.cursor.equals(new Coordinate(col - 1, row - 1))) {
            Border border = BorderFactory.createLineBorder(Color.magenta, 2);
            label.setBorder(border);
        }

        return label;
    }

    private Color contrastColor(Color color)
    {
        // Counting the perceptive luminance - human eye favors green color...
        double a = 1 - (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;

        int d;
        if (a < 0.5)
            d = 0; // bright colors - black font
        else
            d = 255; // dark colors - white font

        return new Color(d, d, d);
    }
}

public class CompressorDebugger extends Compressor
{
    private JTable liveTable;

    private DefaultTableModel liveTableModel;

    private JTable expectedTable;

    private DefaultTableModel expectedTableModel;

    private JLabel nbCommands;

    private JLabel cursorPosition;

    private int imageColumns;

    private int imageRows;

    private int tableColumns;

    private int tableRows;

    int tableWidth;

    int tableHeight;

    int frameWidth;

    int frameHeight;

    private boolean isPaused = false;

    private int idleTime = 250;

    private int cellSize = 15;

    private int currentCommandIndex = -1;

    CompressorDebugger(Image image)
    {
        super(image);

        JFrame df = debuggerFrame();

        update();

        df.setVisible(true);
    }

    private JFrame debuggerFrame()
    {
        imageColumns = this.drawing.width;
        imageRows = this.drawing.height;

        tableColumns = imageColumns + 1;
        tableRows = imageRows + 1;

        tableWidth = tableColumns * cellSize;
        tableHeight = tableRows * cellSize;

        frameWidth = tableWidth * 2 + 150 + 3;
        frameHeight = tableHeight + 50;

        // Creating Frame
        JFrame frame = new JFrame();
        frame.setLayout(new BorderLayout());
        frame.setSize(frameWidth, frameHeight);
        frame.setBackground(Color.lightGray);
        frame.setResizable(false);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // Tables container
        JPanel tables = new JPanel();
        tables.setPreferredSize(new Dimension(tableWidth * 2, tableHeight));
        tables.setLayout(new BorderLayout());
        tables.setBackground(Color.ORANGE);
        frame.add(tables, BorderLayout.CENTER);

        // Live table
        liveTableModel = new DefaultTableModel();
        liveTableModel.setColumnCount(tableColumns);
        liveTableModel.setRowCount(tableRows);

        for (int x = 0; x < imageColumns; x++) {
            liveTableModel.setValueAt(x, 0, x + 1);
        }

        for (int y = 0; y < imageRows; y++) {
            liveTableModel.setValueAt(y, y + 1, 0);
        }

        liveTable = new JTable(liveTableModel);
        liveTable.setPreferredSize(new Dimension(tableWidth, tableHeight));
        liveTable.setRowHeight(cellSize);
        liveTable.setFocusable(false);
        liveTable.setRowSelectionAllowed(false);
        liveTable.setDefaultRenderer(Object.class, new PixelCellRenderer(this));
        tables.add(liveTable, BorderLayout.EAST);

        // Expected result table
        expectedTableModel = new DefaultTableModel();
        expectedTableModel.setColumnCount(tableColumns);
        expectedTableModel.setRowCount(tableRows);

        for (int x = 0; x < imageColumns; x++) {
            expectedTableModel.setValueAt(x, 0, x + 1);
        }

        for (int y = 0; y < imageRows; y++) {
            expectedTableModel.setValueAt(y, y + 1, 0);
        }

        expectedTable = new JTable(expectedTableModel);
        expectedTable.setPreferredSize(new Dimension(tableWidth, tableHeight));
        expectedTable.setRowHeight(cellSize);
        expectedTable.setFocusable(false);
        expectedTable.setRowSelectionAllowed(false);
        expectedTable.setDefaultRenderer(Object.class, new PixelCellRenderer(this));
        expectedTable.setBorder(new EmptyBorder(0, 0, 0, 3));
        tables.add(expectedTable, BorderLayout.WEST);

        // Controls
        JPanel controls = new JPanel();
        controls.setPreferredSize(new Dimension(tableWidth, 50));
        controls.setLayout(new BorderLayout());
        frame.add(controls, BorderLayout.SOUTH);

        JSlider slider = new JSlider();
        slider.setMinimum(0);
        slider.setMaximum(500);
        slider.setValue(idleTime);
        slider.setPreferredSize(new Dimension(frameWidth * 2 / 3, 40));
        slider.setMajorTickSpacing(50);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.addChangeListener(e -> {
            JSlider source = (JSlider) e.getSource();
            idleTime = source.getValue();
        });
        controls.add(slider, BorderLayout.WEST);

        JButton pauseButton = new JButton();
        pauseButton.setText("Pause");
        pauseButton.addActionListener(e -> {
            isPaused = !isPaused;
            JButton button = (JButton) e.getSource();
            button.setText(isPaused ? "Play" : "Pause");
        });
        controls.add(pauseButton, BorderLayout.EAST);

        JPanel infosPanel = new JPanel();
        infosPanel.setPreferredSize(new Dimension(150, frameHeight));
        infosPanel.setLayout(new FlowLayout());
        frame.add(infosPanel, BorderLayout.EAST);

        nbCommands = new JLabel();
        infosPanel.add(nbCommands);

        cursorPosition = new JLabel();
        infosPanel.add(cursorPosition);

        return frame;
    }

    @Override
    protected void addCommand(Direction direction, int distance, boolean paint, int color)
    {
        super.addCommand(direction, distance, paint, color);
        this.update();

        try {
            Thread.sleep(idleTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isPaused()
    {
        return isPaused;
    }

    @Override
    public Drawing compress()
    {
        Drawing d = super.compress();

        done();

        return d;
    }

    private void done()
    {
        boolean result = false;
        try {
            result = Objects.equals(image.toString(), drawing.draw().toString());
        } catch (BadCommand badCommand) {
            badCommand.printStackTrace();
        }

        JOptionPane.showMessageDialog(null, result ? "Done !" : "FAIL");
    }

    public void update()
    {
        nbCommands.setText("Nb Commands: " + this.drawing.commands.size());
        cursorPosition.setText("Cursor: " + this.cursor);

        Drawing d = this.drawing;

        if (currentCommandIndex != -1) {
            Drawing tmpDrawing = new Drawing(d.width, d.height, d.background);
            for (int i = 0; i <= currentCommandIndex; i++) {
                tmpDrawing.addCommand(d.commands.get(i));
            }
        }

        Image image = null;
        try {
            image = d.draw();
        } catch (BadCommand badCommand) {
            badCommand.printStackTrace();
        }

//        // Following commands
//        for (int y = 0; y < imageRows; y++) {
//            for (int x = 0; x < imageColumns; x++) {
//                liveTableModel.setValueAt(image.get(x, y), y + 1, x + 1);
//            }
//        }

        // Drawn Coordinates
        for (int y = 0; y < imageRows; y++) {
            for (int x = 0; x < imageColumns; x++) {
                Coordinate c = new Coordinate(x, y);
                Integer v = null;
                if (drawnCoordinates.contains(c)) {
                    v = image.get(x, y);
                }

                liveTableModel.setValueAt(v, y + 1, x + 1);
            }
        }


        this.updateExpected();
    }

    public void updateExpected()
    {
        Image image = this.image;

        for (int y = 0; y < imageRows; y++) {
            for (int x = 0; x < imageColumns; x++) {
                expectedTableModel.setValueAt(image.get(x, y), y + 1, x + 1);
            }
        }
    }
}
