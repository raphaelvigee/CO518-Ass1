import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

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

        if (row == 0 || col == 0) {
            label.setBackground(Color.white);
            label.setForeground(Color.black);

            return c;
        }

        if (value != null) {
            label.setBackground(new Color(Image.colours[(int) value]));
        }

        label.setForeground(new Color(0, 0, 0, 0));

        if (compressor.cursor.equals(new Coordinate(col - 1, row - 1))) {
            Border border = BorderFactory.createLineBorder(Color.BLUE, 2);
            label.setBorder(border);
        }

        return label;
    }
}

public class CompressorDebugger extends Compressor
{
    private JTable table;

    private DefaultTableModel tableModel;

    private JLabel nbCommands;

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

    private int cellSize = 20;

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

        frameWidth = tableWidth + 150;
        frameHeight = tableHeight + 50;

        //Creating Frame
        JFrame frame = new JFrame();
        frame.setLayout(new BorderLayout());
        frame.setSize(frameWidth, frameHeight);
        frame.setBackground(Color.lightGray);
        frame.setResizable(false);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        tableModel = new DefaultTableModel();
        tableModel.setColumnCount(tableColumns);
        tableModel.setRowCount(tableRows);

        for (int x = 0; x < imageColumns; x++) {
            tableModel.setValueAt(x, 0, x + 1);
        }

        for (int y = 0; y < imageRows; y++) {
            tableModel.setValueAt(y, y + 1, 0);
        }

        table = new JTable(tableModel);
        table.setRowHeight(cellSize);
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.setDefaultRenderer(Object.class, new PixelCellRenderer(this));
        frame.add(table, BorderLayout.CENTER);

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

        return frame;
    }

    @Override
    protected void addCommand(Direction direction, int distance, boolean paint, int color)
    {
        try {
            Thread.sleep(idleTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        super.addCommand(direction, distance, paint, color);
        this.update();
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
        JOptionPane.showMessageDialog(null, "Done !");
    }

    public void update()
    {
        nbCommands.setText("Nb Commands: " + this.drawing.commands.size());

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

        for (int y = 0; y < imageRows; y++) {
            for (int x = 0; x < imageColumns; x++) {
                tableModel.setValueAt(image.get(x, y), y + 1, x + 1);
            }
        }
    }
}
