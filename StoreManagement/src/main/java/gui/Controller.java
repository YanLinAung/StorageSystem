package gui;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;

import exceptions.NoNameForProductException;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import parts.IllegalStringReplacer;
import parts.Item;
import save_load.JSONLoader;
import save_load.Printing;
import save_load.SaveToFile;
import save_load.SaveToFile.PrintOutType;

public class Controller implements Initializable {

	@FXML
	ListView<ItemBox> listView = new ListView<ItemBox>();
	@FXML
	ToggleGroup addRemoveToggle;
	@FXML
	ToggleGroup searchToggle;
	@FXML
	ToggleButton addButton;
	@FXML
	ToggleButton removeButton;
	@FXML
	TextField searchBox;
	@FXML
	RadioButton nameSearch;
	@FXML
	RadioButton amountSearch;
	@FXML
	RadioButton barcodeSearch;
	@FXML
	RadioButton categorieSearch;
	@FXML
	MenuItem loadMenu;
	@FXML
	MenuItem saveMenu;
	@FXML
	MenuItem exitMenu;
	@FXML
	MenuItem updateMenu;
	@FXML
	MenuItem updateAllMenu;
	@FXML
	MenuItem deleteMenu;
	@FXML
	MenuItem groupByMenu;
	@FXML
	MenuItem repeatMenu;
	@FXML
	MenuItem aboutMenu;
	@FXML
	MenuItem printMenu;
	@FXML
	MenuItem printShoppingMenu;
	@FXML
	Label nameLabel;
	@FXML
	Label gtinLabel;
	@FXML
	Label amountLabel;
	@FXML
	Label categoriesLabel;
	@FXML
	Label attributesLabel;

	ObservableMap<String, ItemBox> itemsMap = FXCollections.observableMap(new HashMap<String, ItemBox>());
	ObservableList<ItemBox> items = FXCollections.observableArrayList(itemsMap.values());
	ObservableList<ItemBox> searchItems = FXCollections.observableArrayList();
	private static String lastCommand;
	private static Logger log = LogManager.getLogger(Controller.class);

	/**
	 * defines what happens, before the GUI is started
	 */
	public void initialize(URL location, ResourceBundle resources) {

		// set the controller var in the Main class to this Controller
		Main.controller = this;

		updateList();
		log.debug("List updated");

		// Formatting of the listView
		listView.setFixedCellSize(60);
		listView.setItems(items);
		addButton.setSelected(true);
		log.debug("ListView cellSize changed, items assigned");

		// is activated if the text in the searchbox is changed
		searchBox.textProperty().addListener((observable, oldVal, newVal) -> {
			renewSearch(newVal);
		});

		// is called when the selected Search Radiobutton is changed
		searchToggle.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
			public void changed(ObservableValue<? extends Toggle> ov, Toggle old_toggle, Toggle new_toggle) {
				renewSearch(searchBox.getText());
			}
		});

		// gets called if an item in the listview is selected -> will load the
		// currently selected item
		// in the overview on the left
		listView.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<ItemBox>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends ItemBox> c) {
				//Update the overview section on the left side of the GUI
				updateOverview();
			}
		});

		setupMenuItems();
	}
	
	/**
	 * Updates the GUI sections labels on the left side of the GUI with the currently selected Item
	 */
	private void updateOverview() {
		
		//check if anything is selected
		if (!listView.getSelectionModel().isEmpty()) {
			ItemBox itemBox = listView.getSelectionModel().getSelectedItem();

			nameLabel.setText(itemBox.getName());
			amountLabel.setText(String.valueOf(itemBox.getAmount()) + "x");
			gtinLabel.setText(itemBox.getGtin());
			categoriesLabel.setText(itemBox.getCategoriesText("long"));
			attributesLabel.setText(itemBox.getAttributes());
			log.info("Overview set to " + itemBox.getName());
		}	
	}

	/**
	 * Sets up the behaviour of the Items int the menubar
	 */
	private void setupMenuItems() {
		aboutMenu.setOnAction(event -> {
			AnchorPane root;
			try {
				root = FXMLLoader.load(getClass().getResource("about.fxml"));

				Stage stage = new Stage();
				stage.setScene(new Scene(root));
				stage.setTitle("About");
				stage.show();
			} catch (Exception e) {
				log.error("Error loading About Window - " + e.getMessage());
			}
		});

		exitMenu.setOnAction(event -> {
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					Platform.exit();
					log.debug("Window closed");
				}
			});
		});

		loadMenu.setOnAction(event -> loadFile(true));

		saveMenu.setOnAction(event -> Main.serializeItems());

		groupByMenu.setOnAction(event -> {
			Optional<String> sortOption = Alerter.getChoiceDialog("Sorting", null, "Select how you want to group: ");
			sortOption.ifPresent(letter -> groupItems(letter));
		});

		updateAllMenu.setOnAction(event -> {
			new Thread(new Task<Void>() {
				@Override
				protected Void call() throws Exception {
					log.debug("UpdateAll Thread Triggered");

					itemsMap.forEach((a, b) -> {
						updateItem(b);
					});

					updateList();
					Main.serializeItems();

					log.debug("UpdateAll Thread terminated successfully");
					return null;
				}
			}).start();
		});

		updateMenu.setOnAction(event -> {
			if (!listView.getSelectionModel().isEmpty()) {
				ItemBox itemBox = listView.getSelectionModel().getSelectedItem();
				updateItem(itemBox);
			} else {
				Alert alert = Alerter.getAlert(AlertType.INFORMATION, "No Item selected", null,
						"Please select the Item you want to update!");
				alert.showAndWait();
				log.debug("Info Popup triggered, No item selected");
			}
		});

		deleteMenu.setOnAction(event -> {
			ItemBox rem = itemsMap.remove(listView.getSelectionModel().getSelectedItem().getGtin());
			log.info("Item: " + rem.getName() + " removed");
			updateList();
		});

		repeatMenu.setOnAction(event -> {
			if (lastCommand != null) {
				String[] props = lastCommand.split(" ");
				log.info("Repeat called with: " + lastCommand);

				switch (props[0]) {
				case "ADD":
					addItem(props[1]);
					break;
				case "RM":
					removeItem(props[1]);
					break;
				}
			}
		});

		printMenu.setOnAction(event -> {
			printOut(PrintOutType.OVERVIEW);
		});

		printShoppingMenu.setOnAction(event -> {
			printOut(PrintOutType.SHOPPING);
		});

	}

	/**
	 * Updates the Item in the ItemBox itemBox
	 * 
	 * @param itemBox
	 */
	private void updateItem(ItemBox itemBox) {
		try {
			Item temp = getNewItem(itemBox.getGtin());

			// checks if the currently fetched temp Item is the still the same
			// item as the one online
			if (!temp.equals(itemBox.getItem())) {
				log.info(temp.name + " unequal to " + itemBox.getItem().name);
				itemBox.setItem(temp);
				log.info("Changed to " + temp.name);
			}
		} catch (Exception e1) {
			log.error("Error updating Item " + itemBox.getName() + " - " + e1.getMessage());
		}
	}

	/**
	 * prints out either the shopping List or the Overview of the items in stock
	 * depending on the type
	 * 
	 * @param type
	 */
	public void printOut(PrintOutType type) {

		boolean output = false;
		String fileToPrint = "";

		switch (type) {
		case OVERVIEW:
			output = SaveToFile.printOut(new ArrayList<ItemBox>(items), type, false);
			fileToPrint = "Overview.txt";
			break;
		case SHOPPING:
			ArrayList<ItemBox> temp = new ArrayList<>();

			// adds every Item which has an amount of 0 to the shopping list
			for (ItemBox item : items) {
				if (item.getAmount() == 0) {
					temp.add(item);
				}
			}
			output = SaveToFile.printOut(temp, type, false);
			fileToPrint = "Shopping.txt";
			break;
		}

		if (output) {
			log.debug(type.name() + " Successfully Saved PrintFile");
			File file = new File(System.getProperty("user.home") + "/Desktop/" + fileToPrint);

			if (file != null) {
				boolean printOut = Printing.printFile(file);

				if (printOut) {
					log.debug("Successfully printed File");
					Alert alert = Alerter.getAlert(AlertType.INFORMATION, "Print", null, "Successfully printed.");
					alert.showAndWait();
				} else {
					log.debug("File was saved but could not be printed");
					Alert alert = Alerter.getAlert(AlertType.INFORMATION, "Print Failed", null,
							"File saved but could not be printed.");
					alert.showAndWait();
				}
			}

		} else {
			Alert alert = Alerter.getAlert(AlertType.INFORMATION, "Error", null,
					"File could not be saved and printed.");
			alert.showAndWait();
			log.debug(type.name() + "PrintFile could NOT be saved and printed");
		}
	}

	/**
	 * updates the items we search for with the text in the searchbox and the
	 * currently selected Searchradiobutton
	 * 
	 * @param newVal
	 */
	public void renewSearch(String newVal) {
		listView.getSelectionModel().clearSelection();
		searchItems.clear();

		if (newVal.equals("")) {
			listView.setItems(items);
			log.info("All items are displayed");
		} else {

			if (searchToggle.getSelectedToggle().equals(nameSearch)) {
				itemsMap.forEach((a, b) -> {
					if (b.getName().toLowerCase().contains(newVal.toLowerCase())) {
						searchItems.add(b);
					}
				});
				log.info("Only items with '" + newVal + "' in their name are displayed");

			} else if (searchToggle.getSelectedToggle().equals(amountSearch)) {
				itemsMap.forEach((a, b) -> {
					if (String.valueOf(b.getAmount()).contains(newVal)) {
						searchItems.add(b);
					}
				});
				log.info("Only items with '" + newVal + "' as their amount are displayed");

			} else if (searchToggle.getSelectedToggle().equals(barcodeSearch)) {
				itemsMap.forEach((a, b) -> {
					if (b.getGtin().contains(newVal)) {
						searchItems.add(b);
					}
				});
				log.info("Only items with '" + newVal + "' in their barcode are displayed");

			} else if (searchToggle.getSelectedToggle().equals(categorieSearch)) {
				itemsMap.forEach((a, b) -> {
					for (String cat : b.getCategories()) {
						if (cat.toLowerCase().contains(newVal.toLowerCase())) {
							searchItems.add(b);
							break;
						}
					}
				});
				log.info("Only items with '" + newVal + "' in their categories are displayed");
			}

			listView.setItems(searchItems);
		}
	}

	/**
	 * sorts the items in the list in the with the choicedialog selected order
	 * 
	 * @param order
	 */
	public void groupItems(String order) {

		log.debug("Grouping valled with " + order);
		ArrayList<ItemBox> temp = new ArrayList<>(items);

		switch (order) {
		case "Name":
			Collections.sort(temp, (a, b) -> {
				return a.getName().compareTo(b.getName());
			});
			log.info("Grouped by Name");
			break;
		case "Amount":
			Collections.sort(temp, (a, b) -> {
				return a.getAmount() - b.getAmount();
			});
			log.info("Grouped by Amount");
			break;
		case "Categorie":
			Collections.sort(temp, (a, b) -> {
				if (a.getCategories().length == 0 && b.getCategories().length == 0) {
					return -1;
				} else if (a.getCategories().length == 0) {
					return 1;
				} else if (b.getCategories().length == 0) {
					return -1;
				} else {
					return a.getCategories()[0].compareTo(b.getCategories()[0]);
				}
			});
			log.info("Grouped by Categorie");
			break;
		}

		items = FXCollections.observableArrayList(temp);
		listView.setItems(items);
	}

	public void loadFile(boolean state) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {

				log.debug("Loading File");

				List<Item> temp = JSONLoader.load(state);

				if (temp != null) {
					for (Item item : temp) {
						itemsMap.put(item.gtin, new ItemBox(item));
					}
					updateList();
				}
				log.debug("File Loaded");
			}
		});
	}

	/**
	 * with the help of the Google Gson libary, the JSON gets translated into a
	 * new Item pulled from Outpan.com with the passed Barcode
	 * 
	 * @param gtin
	 * @return
	 * @throws IOException
	 */
	private Item getNewItem(String gtin) throws IOException {

		log.info("Getting Item with Barcode: " + gtin);
		Gson gson = new Gson();
		URL url = new URL("https://api.outpan.com/v2/products/" + gtin + "?apikey=e13a9fb0bda8684d72bc3dba1b16ae1e");

		StringBuilder temp = new StringBuilder();
		Scanner scanner = new Scanner(url.openStream());

		while (scanner.hasNext()) {
			temp.append(scanner.nextLine());
		}
		scanner.close();

		Item item = new Item(gson.fromJson(temp.toString(), Item.class));
		
		if (item.name != null) {
			return item;
		} else {
			throw new NoNameForProductException();
		}
	}

	public boolean addItem(String gtin) {
		lastCommand = "ADD " + gtin;
		log.debug("LastCommand set to: " + lastCommand);

		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				log.info("Add: " + gtin);
				try {
					if (!itemsMap.containsKey(gtin)) {
						itemsMap.put(gtin, new ItemBox(getNewItem(gtin)));
						updateList();
						listView.getSelectionModel().select(itemsMap.get(gtin));
					} else {
						itemsMap.get(gtin).increaseAmount();
						listView.getSelectionModel().select(itemsMap.get(gtin));
						updateOverview();
					}
				} catch (NoNameForProductException e) {
					log.error("Item not Found");

					Optional<String> result = Alerter.getTextDialog("Item not Found", "The Item is not yet listed",
							"Please enter the name of the Product:");
					result.ifPresent(name -> listNewItem(gtin, name));

				} catch (IOException e) {
					log.debug("Entered Barcode is not Valid");
					Alert alert = Alerter.getAlert(AlertType.WARNING, "Not a valid Barcode", null,
							"The entered Barcode is not valid.\nPlease try again");
					alert.showAndWait();
				}
			}
		});
		return false;
	}

	/**
	 * if the barcode hasn't already got a name on outpan.com the item will be
	 * listed online with the passed name
	 * 
	 * @param gtin
	 * @param name
	 */
	private void listNewItem(String gtin, String name) {
		try {
			URL url = new URL("https://api.outpan.com/v2/products/" + gtin + "/name"
					+ "?apikey=e13a9fb0bda8684d72bc3dba1b16ae1e");

			HttpsURLConnection httpCon = (HttpsURLConnection) url.openConnection();
			httpCon.setDoOutput(true);
			httpCon.setRequestMethod("POST");
			
			//replaces umlauts, ß, ", ' and / 
			name = IllegalStringReplacer.replaceIllegalChars(name);

			String content = "name=" + name;
			DataOutputStream out = new DataOutputStream(httpCon.getOutputStream());

			out.writeBytes(content);
			out.flush();

			log.debug(httpCon.getResponseCode() + " - " + httpCon.getResponseMessage());
			out.close();

			if (httpCon.getResponseCode() == 200) {
				Alert alert = Alerter.getAlert(AlertType.INFORMATION, "Item Added", null, "Item is now listed.");
				alert.showAndWait();
				log.info("Item '" + name + "' now listed");

				addItem(gtin);
			} else {
				log.debug("Item could not be listed");
				Alert alert = Alerter.getAlert(AlertType.WARNING, "Item not Added", null,
						"Item could not be listed, please try again.");
				alert.showAndWait();
			}

		} catch (MalformedURLException e) {
			log.error("MalformedURLException: " + e.getMessage());
		} catch (IOException e) {
			log.error("IOException: " + e.getMessage());
		}
	}

	public void updateList() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				items = FXCollections.observableArrayList(itemsMap.values());
				listView.setItems(items);
				log.info("List updated");
			}
		});
	}

	public boolean removeItem(String gtin) {

		lastCommand = "RM " + gtin;

		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				log.info("Remove: " + gtin);

				if (itemsMap.containsKey(gtin)) {
					if (itemsMap.get(gtin).getAmount() == 2) {
						log.info("One Item of '" + gtin + "' left");
						Alert alert = Alerter.getAlert(AlertType.INFORMATION, "Only one Item left", null,
								"Only one of this Item is left in Stock");
						alert.showAndWait();
					} else if (itemsMap.get(gtin).getAmount() == 1) {
						log.info("No Item of '" + gtin + "' left");
						Alert alert = Alerter.getAlert(AlertType.INFORMATION, "Last Item removed", null,
								"This was the last one of this Item\nPlease rebuy");
						alert.showAndWait();
					} else if (itemsMap.get(gtin).getAmount() == 0) {
						log.info("No Item '" + gtin + "' in Stock");
						Alert alert = Alerter.getAlert(AlertType.WARNING, "No more Item", null,
								"No more item of this kind in stock");
						alert.showAndWait();
					}

					itemsMap.get(gtin).decreaseAmount();
					listView.getSelectionModel().select(itemsMap.get(gtin));

				} else {
					log.debug("Item '" + gtin + "' not found");
					Alert alert = Alerter.getAlert(AlertType.WARNING, "No Item Found", null,
							"There is no Item with this Barcode");
					alert.showAndWait();
				}
			}
		});
		return false;
	}

}
