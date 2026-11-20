"""Windows UI Automation for the typing engine: is the focused text box a
password field, and what text is just before its caret (works in browsers,
Office, Telegram and most modern apps).

Plain COM through ctypes: ctypes releases the GIL during the call, so a
slow or frozen app never delays the keyboard hook."""
import ctypes
import uuid
from ctypes import wintypes as w

ole32 = ctypes.OleDLL("ole32")
oleaut32 = ctypes.OleDLL("oleaut32")
oleaut32.SysStringLen.argtypes = [ctypes.c_void_p]
oleaut32.SysStringLen.restype = ctypes.c_uint
oleaut32.SysFreeString.argtypes = [ctypes.c_void_p]
oleaut32.SysFreeString.restype = None

CLSID_CUIAutomation = uuid.UUID("ff48dba4-60ef-4201-aa87-54103eef594e")
IID_IUIAutomation = uuid.UUID("30cbe57d-d9d0-452a-ab13-7ac5ac4825ee")
IID_IUIAutomationTextPattern = uuid.UUID("32eba289-3583-42c9-9c59-3b6d9a1e9b6a")
IID_IUIAutomationTextPattern2 = uuid.UUID("506a921a-fcc9-409f-b23b-37eb74106872")
UIA_ProcessIdPropertyId = 30002
UIA_IsPasswordPropertyId = 30019
UIA_TextPatternId, UIA_TextPattern2Id = 10014, 10024
TextPatternRangeEndpoint_Start, TextPatternRangeEndpoint_End = 0, 1
TextUnit_Character = 0
VT_BOOL, VT_I4 = 11, 3

# vtable slots (UIAutomationClient.h); every interface starts with IUnknown 0-2
_RELEASE = 2
_GET_FOCUSED = 8  # IUIAutomation.GetFocusedElement
_GET_PROPERTY, _GET_PATTERN_AS = 10, 14  # IUIAutomationElement.GetCurrentPropertyValue, GetCurrentPatternAs
_GET_SELECTION = 5  # IUIAutomationTextPattern.GetSelection
_GET_CARET_RANGE = 10  # IUIAutomationTextPattern2.GetCaretRange
_ARRAY_LENGTH, _ARRAY_ELEMENT = 3, 4  # IUIAutomationTextRangeArray.get_Length, GetElement
# IUIAutomationTextRange.CompareEndpoints, GetText, MoveEndpointByUnit
_COMPARE_ENDPOINTS, _GET_TEXT, _MOVE_ENDPOINT = 5, 12, 14

_OUT = ctypes.POINTER(ctypes.c_void_p)
_INT_OUT = ctypes.POINTER(ctypes.c_int)


class GUID(ctypes.Structure):
    _fields_ = [("data", ctypes.c_byte * 16)]

    @classmethod
    def of(cls, u: uuid.UUID):
        g = cls()
        ctypes.memmove(g.data, u.bytes_le, 16)
        return g


class VARIANT(ctypes.Structure):
    _fields_ = [("vt", ctypes.c_ushort), ("r1", ctypes.c_ushort), ("r2", ctypes.c_ushort), ("r3", ctypes.c_ushort),
                ("value", ctypes.c_longlong), ("extra", ctypes.c_longlong)]


def _method(ptr, slot, *argtypes):
    vtable = ctypes.cast(ctypes.cast(ptr, ctypes.POINTER(ctypes.c_void_p))[0], ctypes.POINTER(ctypes.c_void_p))
    return ctypes.WINFUNCTYPE(ctypes.HRESULT, ctypes.c_void_p, *argtypes)(vtable[slot])


def _release(ptr):
    vtable = ctypes.cast(ctypes.cast(ptr, ctypes.POINTER(ctypes.c_void_p))[0], ctypes.POINTER(ctypes.c_void_p))
    ctypes.WINFUNCTYPE(ctypes.c_ulong, ctypes.c_void_p)(vtable[_RELEASE])(ptr)


class Automation:
    """Create and use in one thread (COM apartment)."""

    def __init__(self):
        ole32.CoInitializeEx(None, 0)  # COINIT_MULTITHREADED
        self.ptr = ctypes.c_void_p()
        ole32.CoCreateInstance(ctypes.byref(GUID.of(CLSID_CUIAutomation)), None, 1,
                               ctypes.byref(GUID.of(IID_IUIAutomation)), ctypes.byref(self.ptr))

    def _focused(self):
        """The element that has keyboard focus (the caller releases it), or None."""
        element = ctypes.c_void_p()
        try:
            _method(self.ptr, _GET_FOCUSED, _OUT)(self.ptr, ctypes.byref(element))
        except OSError:
            return None
        return element if element.value else None

    def focused_property(self, prop: int):
        """A property of the element that has keyboard focus (bool/int), or None."""
        element = self._focused()
        if not element:
            return None
        try:
            v = VARIANT()
            _method(element, _GET_PROPERTY, ctypes.c_int, ctypes.POINTER(VARIANT))(element, prop, ctypes.byref(v))
            try:
                if v.vt == VT_BOOL:
                    return bool(v.value & 0xFFFF)
                if v.vt == VT_I4:
                    return ctypes.c_int32(v.value & 0xFFFFFFFF).value
                return None
            finally:
                oleaut32.VariantClear(ctypes.byref(v))
        except OSError:
            return None
        finally:
            _release(element)

    def focused_is_password(self) -> bool:
        return bool(self.focused_property(UIA_IsPasswordPropertyId))

    def text_before_caret(self, n: int) -> str | None:
        """Up to `n` characters just before the caret of the focused text box
        (fewer at the start of the text), or None when we cannot tell: no text
        pattern, text selected, an error. Never raises."""
        held = []  # every COM pointer we get, released at the end
        try:
            element = self._focused()
            if not element:
                return None
            held.append(element)
            caret = self._caret_range(element, held)
            if not caret:
                return None
            moved = ctypes.c_int()
            _method(caret, _MOVE_ENDPOINT, ctypes.c_int, ctypes.c_int, ctypes.c_int, _INT_OUT)(
                caret, TextPatternRangeEndpoint_Start, TextUnit_Character, -n, ctypes.byref(moved))
            text = ctypes.c_void_p()  # BSTR
            _method(caret, _GET_TEXT, ctypes.c_int, _OUT)(caret, n, ctypes.byref(text))
            if not text.value:
                return ""
            try:
                return ctypes.wstring_at(text.value, oleaut32.SysStringLen(text))
            finally:
                oleaut32.SysFreeString(text)
        except Exception:
            return None
        finally:
            for ptr in reversed(held):
                try:
                    _release(ptr)
                except Exception:
                    pass

    @staticmethod
    def _pattern(element, pattern_id: int, iid: uuid.UUID, held: list):
        """The element's control pattern as interface `iid`, or None."""
        pattern = ctypes.c_void_p()
        try:
            _method(element, _GET_PATTERN_AS, ctypes.c_int, ctypes.POINTER(GUID), _OUT)(
                element, pattern_id, ctypes.byref(GUID.of(iid)), ctypes.byref(pattern))
        except OSError:
            return None
        if not pattern.value:
            return None  # the element does not support this pattern
        held.append(pattern)
        return pattern

    def _caret_range(self, element, held: list):
        """An empty text range at the caret, or None. Every pointer goes into `held`."""
        pattern = self._pattern(element, UIA_TextPattern2Id, IID_IUIAutomationTextPattern2, held)
        if pattern:  # the program tells us where the caret is
            active, caret = w.BOOL(), ctypes.c_void_p()
            try:
                _method(pattern, _GET_CARET_RANGE, ctypes.POINTER(w.BOOL), _OUT)(
                    pattern, ctypes.byref(active), ctypes.byref(caret))
            except OSError:
                pass
            if caret.value:
                held.append(caret)
                return caret
        # Older programs: the caret is where the (empty) selection is
        pattern = self._pattern(element, UIA_TextPatternId, IID_IUIAutomationTextPattern, held)
        if not pattern:
            return None
        ranges = ctypes.c_void_p()
        _method(pattern, _GET_SELECTION, _OUT)(pattern, ctypes.byref(ranges))
        if not ranges.value:
            return None
        held.append(ranges)
        count = ctypes.c_int()
        _method(ranges, _ARRAY_LENGTH, _INT_OUT)(ranges, ctypes.byref(count))
        if count.value != 1:
            return None
        caret = ctypes.c_void_p()
        _method(ranges, _ARRAY_ELEMENT, ctypes.c_int, _OUT)(ranges, 0, ctypes.byref(caret))
        if not caret.value:
            return None
        held.append(caret)
        diff = ctypes.c_int()
        _method(caret, _COMPARE_ENDPOINTS, ctypes.c_int, ctypes.c_void_p, ctypes.c_int, _INT_OUT)(
            caret, TextPatternRangeEndpoint_Start, caret, TextPatternRangeEndpoint_End, ctypes.byref(diff))
        return caret if diff.value == 0 else None  # text is selected: we cannot tell which end has the caret

    def close(self):
        if self.ptr.value:
            _release(self.ptr)
            self.ptr = ctypes.c_void_p()
        ole32.CoUninitialize()
