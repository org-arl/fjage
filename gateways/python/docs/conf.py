import os
import sys
from datetime import datetime

# -- Path setup --------------------------------------------------------------

# Add the package root so autodoc can import fjagepy when building docs.
CURRENT_DIR = os.path.abspath(os.path.dirname(__file__))
PKG_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, '..'))
sys.path.insert(0, PKG_ROOT)

# -- Project information -----------------------------------------------------

project = 'fjagepy'
author = 'Subnero / ARL'  # update if needed
copyright = f"{datetime.now().year}, {author}"

# Attempt to get the version from installed project metadata or pyproject.
try:
    from importlib.metadata import version as _pkg_version
    release = _pkg_version('fjagepy')
except Exception:
    # Fallback to pyproject version or unknown
    release = 'unknown'
version = release

# -- General configuration ---------------------------------------------------

extensions = [
    'sphinx.ext.autodoc',
    'sphinx.ext.napoleon',
    'sphinx.ext.autosummary',
    'sphinx.ext.viewcode',
    'myst_parser',
]

autosummary_generate = True
autodoc_typehints = 'description'
napoleon_google_docstring = True
napoleon_numpy_docstring = True
napoleon_use_param = True
napoleon_use_rtype = True

templates_path = ['_templates']
exclude_patterns = ['_build', 'Thumbs.db', '.DS_Store']

source_suffix = {
    '.rst': 'restructuredtext',
    '.md': 'markdown',
}
master_doc = 'index'

# -- Options for HTML output -------------------------------------------------

html_theme = 'sphinx_rtd_theme'
html_static_path = ['_static']
html_title = 'fjagepy Documentation'
