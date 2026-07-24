import sys
import threading
import requests
from kivy.app import App
from kivy.clock import Clock
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.textinput import TextInput
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.widget import Widget
from kivy.graphics import Color, Rectangle, RoundedRectangle
from kivy.core.window import Window

# Константа для ключа Spectr AI API (DeepSeek). 
SPECTR_API_KEY = "sk-ced3de9dd83a466b852c185dd29a7b7d"

# Установка цвета фона окна по умолчанию
Window.clearcolor = (0.07, 0.07, 0.07, 1)


class ColoredBoxLayout(BoxLayout):
    """BoxLayout с возможностью динамического изменения цвета фона."""
    def __init__(self, bg_color=(0.07, 0.07, 0.07, 1), **kwargs):
        super().__init__(**kwargs)
        with self.canvas.before:
            self.rect_color = Color(*bg_color)
            self.rect = Rectangle(size=self.size, pos=self.pos)
        self.bind(size=self._update_rect, pos=self._update_rect)

    def _update_rect(self, instance, value):
        self.rect.pos = self.pos
        self.rect.size = self.size

    def set_bg_color(self, bg_color):
        self.rect_color.rgba = bg_color


class MessageBubble(BoxLayout):
    """Кастомный баббл для сообщений в чате."""
    def __init__(self, text, is_user=True, is_dark_theme=True, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint_y = None
        self.padding = [16, 12, 16, 12]
        self.is_user = is_user
        
        if is_dark_theme:
            self.bg_color = (0.13, 0.13, 0.13, 1) if is_user else (0.10, 0.10, 0.10, 1)
            self.text_color = (1, 1, 1, 1)
        else:
            self.bg_color = (0.88, 0.88, 0.88, 1) if is_user else (0.94, 0.94, 0.94, 1)
            self.text_color = (0, 0, 0, 1)

        with self.canvas.before:
            self.rect_color = Color(*self.bg_color)
            self.rect = RoundedRectangle(pos=self.pos, size=self.size, radius=[14])
        self.bind(pos=self._update_rect, size=self._update_rect)

        self.label = Label(
            text=text,
            color=self.text_color,
            font_size=15,
            font_name="sans-serif",
            size_hint_y=None,
            halign="left" if not is_user else "right",
            valign="top"
        )
        self.label.bind(width=lambda instance, value: setattr(instance, 'text_size', (value, None)))
        self.label.bind(texture_size=self._update_label_height)
        self.add_widget(self.label)

    def _update_rect(self, instance, value):
        self.rect.pos = self.pos
        self.rect.size = self.size

    def _update_label_height(self, instance, value):
        instance.height = value[1]
        self.height = value[1] + self.padding[1] + self.padding[3]

    def set_theme(self, is_dark_theme):
        if is_dark_theme:
            self.bg_color = (0.13, 0.13, 0.13, 1) if self.is_user else (0.10, 0.10, 0.10, 1)
            self.text_color = (1, 1, 1, 1)
        else:
            self.bg_color = (0.88, 0.88, 0.88, 1) if self.is_user else (0.94, 0.94, 0.94, 1)
            self.text_color = (0, 0, 0, 1)
        
        self.rect_color.rgba = self.bg_color
        self.label.color = self.text_color


class MessageRow(BoxLayout):
    """Строка сообщения для корректного выравнивания баббла."""
    def __init__(self, text, is_user=True, is_dark_theme=True, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'horizontal'
        self.size_hint_y = None
        self.spacing = 10
        self.is_user = is_user

        self.bubble = MessageBubble(text, is_user=is_user, is_dark_theme=is_dark_theme, size_hint_x=0.78)
        self.spacer = Widget(size_hint_x=0.22)

        if is_user:
            self.add_widget(self.spacer)
            self.add_widget(self.bubble)
        else:
            self.add_widget(self.bubble)
            self.add_widget(self.spacer)

        self.bubble.bind(height=self._update_height)

    def _update_height(self, instance, value):
        self.height = value

    def set_theme(self, is_dark_theme):
        self.bubble.set_theme(is_dark_theme)


class SpectrAIApp(App):
    def build(self):
        self.is_dark_theme = True
        self.messages_history = []  # Накопитель контекста
        self.thinking_row = None

        # Главный контейнер с отступом сверху (padding top = 42)
        self.main_container = ColoredBoxLayout(
            bg_color=(0.07, 0.07, 0.07, 1), # #121212
            orientation="vertical",
            padding=[10, 42, 10, 10],
            spacing=10
        )

        # 1. HEADER - только кнопка нового чата
        self.header = BoxLayout(orientation="horizontal", size_hint_y=None, height=50, spacing=10)
        
        # Заполнитель слева для выравнивания
        self.header.add_widget(Widget(size_hint_x=1))
        
        self.new_chat_btn = Button(
            text="+",
            size_hint_x=None,
            width=50,
            background_normal="",
            background_color=(0.12, 0.12, 0.12, 0),
            color=(1, 1, 1, 1),
            font_size=28
        )
        self.new_chat_btn.bind(on_release=self.clear_chat_history)
        self.header.add_widget(self.new_chat_btn)
        self.main_container.add_widget(self.header)

        # 2. ОБЛАСТЬ ЧАТА (ScrollView)
        self.scroll_view = ScrollView(size_hint=(1, 1))
        self.messages_list = BoxLayout(
            orientation="vertical",
            size_hint_y=None,
            spacing=12,
            padding=[4, 8, 4, 8]
        )
        self.messages_list.bind(minimum_height=self.messages_list.setter('height'))
        self.scroll_view.add_widget(self.messages_list)
        self.main_container.add_widget(self.scroll_view)

        self.add_message("Здравствуйте! Чем я могу помочь вам сегодня?", is_user=False)

        # 3. ПАНЕЛЬ ВВОДА
        self.input_panel = BoxLayout(
            orientation="horizontal",
            size_hint_y=None,
            height=50,
            spacing=8
        )

        self.text_input = TextInput(
            hint_text="Сообщение...",
            multiline=False,
            size_hint_x=0.82,
            background_normal="",
            background_active="",
            background_color=(0.16, 0.16, 0.16, 1),
            foreground_color=(1, 1, 1, 1),
            hint_text_color=(0.5, 0.5, 0.5, 1),
            font_name="sans-serif",
            font_size=15,
            padding=[14, 14, 14, 14],
            write_tab=False
        )
        self.text_input.bind(on_text_validate=self.send_request)

        self.send_btn = Button(
            text=">",
            size_hint_x=0.18,
            background_normal="",
            background_color=(0.0, 0.47, 1.0, 1),
            color=(1, 1, 1, 1),
            font_name="sans-serif",
            bold=True,
            font_size=16
        )
        self.send_btn.bind(on_release=self.send_request)

        self.input_panel.add_widget(self.text_input)
        self.input_panel.add_widget(self.send_btn)
        self.main_container.add_widget(self.input_panel)

        return self.main_container

    def add_message(self, text, is_user):
        row = MessageRow(text, is_user=is_user, is_dark_theme=self.is_dark_theme)
        self.messages_list.add_widget(row)
        Clock.schedule_once(self.scroll_to_bottom, 0.1)
        return row

    def scroll_to_bottom(self, dt):
        self.scroll_view.scroll_y = 0

    def clear_chat_history(self, instance=None):
        self.messages_list.clear_widgets()
        self.messages_history = []
        self.add_message("Здравствуйте! Чем я могу помочь вам сегодня?", is_user=False)

    def send_request(self, instance=None):
        query = self.text_input.text.strip()
        if not query:
            return

        self.text_input.text = ""
        self.messages_history.append({"role": "user", "content": query})
        
        self.add_message(query, is_user=True)
        self.thinking_row = self.add_message("Думаю...", is_user=False)
        self.send_btn.disabled = True
        
        threading.Thread(target=self._api_call_worker).start()

    def _api_call_worker(self):
        url = "https://api.deepseek.com/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {SPECTR_API_KEY}",
            "Content-Type": "application/json"
        }
        data = {
            "model": "deepseek-chat",
            "messages": self.messages_history
        }

        try:
            response = requests.post(url, json=data, headers=headers, timeout=45)
            if response.status_code == 200:
                result_json = response.json()
                content = result_json["choices"][0]["message"]["content"]
                Clock.schedule_once(lambda dt: self.update_ui_after_call(content, is_error=False), 0)
            elif response.status_code == 402:
                Clock.schedule_once(lambda dt: self.update_ui_after_call(
                    "Ошибка: Средства на счете API исчерпаны. Пополните баланс.",
                    is_error=True
                ), 0)
            else:
                Clock.schedule_once(lambda dt: self.update_ui_after_call(
                    f"Ошибка сервера (Код: {response.status_code}).",
                    is_error=True
                ), 0)
        except Exception as e:
            Clock.schedule_once(lambda dt: self.update_ui_after_call(
                f"Ошибка: {str(e)}",
                is_error=True
            ), 0)

    def update_ui_after_call(self, result_text, is_error=False):
        if self.thinking_row in self.messages_list.children:
            self.messages_list.remove_widget(self.thinking_row)
            
        self.add_message(result_text, is_user=False)
        
        if not is_error:
            self.messages_history.append({"role": "assistant", "content": result_text})
            
        self.send_btn.disabled = False


if __name__ == "__main__":
    SpectrAIApp().run()
