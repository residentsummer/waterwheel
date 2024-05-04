(ns ^:figwheel-hooks residentsummer.waterwheel
  (:require
   [goog.string :as gstring]
   [goog.dom :as gdom]
   [goog.crypt.base64 :as b64]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   goog.string.format  ;; got "TypeError: ja.format is not a function" in prod build
   ))

(defn blank-water []
  {:name "-"
   :ca ""
   :mg ""
   :hco3 ""})

(defn empty-state []
  {:current-name ""
   :current-water (blank-water)
   :saved-waters []})

(declare app-state save-state)

(defn parse-float [val]
  (let [parsed (some-> val (gstring/replaceAll "," ".") (js/parseFloat))
        result (if (js/isNaN parsed) nil parsed)]
    result))

(defn hardness [ca mg]
  (+ (* ca 2.497) (* mg 4.118)))

(defn alkalinity [hco3]
  (* hco3 0.8202))

(defn update-derived-params [water-params]
  (let [[ca mg hco3] (map #(parse-float (get water-params %)) [:ca :mg :hco3])]
    (if (and ca mg hco3)
      (assoc water-params
             :hardness (hardness ca mg)
             :alkalinity (alkalinity hco3))
      (dissoc water-params :hardness :alkalinity))))

;; GUI components

(defn numeric-input-with-label [label state-path update-cb]
  [:div {:style {:display :flex
                 :flex-direction :row
                 :justify-content :space-between}}
   [:div  [:input {:type "text"
                   :input-mode "decimal"
                   :value (-> @app-state (get-in state-path))
                   :on-change #(swap! app-state (fn [state]
                                                  (-> state
                                                      (assoc-in state-path (-> % .-target .-value))
                                                      update-cb)))}]]
   [:div [:label [:h3 label]]]])

(defn save-current-water [state]
  (let [new-water (assoc (:current-water state)
                         :name (:current-name state))]
    (-> state
        (update :saved-waters conj new-water)
        (assoc :current-water (blank-water)
               :current-name ""))))

(defn save-controls []
  [:div {:style {:display :flex
                 :flex-direction :row
                 :justify-content :space-between}}
   [:div
    [:input {:type "text"
             :placeholder "name it"
             :value (-> @app-state :current-name)
             :on-change #(swap! app-state assoc :current-name (-> % .-target .-value))}]]
   [:div
    [:button.button-outline
     {:disabled (not (not-empty (:current-name @app-state)))
      :on-click #(do (swap! app-state save-current-water)
                     (save-state @app-state))}
     "Save"]]])

(defn results-row [water-params]
  [:tr
   [:td (or (:name water-params) "-")]
   [:td (or (some->> (:hardness water-params) (gstring/format "%.2f")) "-")]
   [:td (or (some->> (:alkalinity water-params) (gstring/format "%.2f")) "-")]])

(defn results []
  [:table
   [:thead [:tr [:th "Name"] [:th "Hardness"] [:th "Alkalinity"]]]
   [:tbody
    (let [state @app-state
          all-waters (concat (:saved-waters state) [(:current-water state)])]
      (for [[idx row] (map vector (range) all-waters)]
        ^{:key idx} [results-row row]))]])

(defn water-form []
  (let [water-update-cb #(update % :current-water update-derived-params)]
    [:div.container
     [:h2 "mg/L to ppm CaCO3"]
     [results]
     [numeric-input-with-label "Ca" [:current-water :ca] water-update-cb]
     [numeric-input-with-label "Mg" [:current-water :mg] water-update-cb]
     [numeric-input-with-label "HCO3" [:current-water :hco3] water-update-cb]
     [save-controls]]))

;; State management

(defn json-dump [clj-data]
  (->> clj-data
       (clj->js)
       (.stringify js/JSON)))

(defn json-load [string]
  (-> (.parse js/JSON string)
      (js->clj :keywordize-keys true)))

(defn load-state []
  (try
    (when-let [encoded-state (not-empty js/window.location.hash)]
      ;; (println "State:" encoded-state)
      (->> (.substring encoded-state 1)
           (b64/decodeString)
           (json-load)
           (map update-derived-params)
           (assoc nil :saved-waters)))
    (catch :default e
      (println "error loading state:" e))))

(defn save-state [state]
  (let [keys-to-save (keys (blank-water))]
    (when-let [waters (not-empty (:saved-waters state))]
      (->> waters
           (map #(select-keys % keys-to-save))
           (json-dump)
           (b64/encodeString)
           (set! (.-hash js/window.location))))))

;; react stuff
(defonce app-state (atom (merge (empty-state) (load-state))))

(defn mount [el]
  (rdom/render [water-form] el))

(defn mount-app-element []
  (when-let [el (gdom/getElement "app")]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
